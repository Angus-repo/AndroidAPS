package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.forEach
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.core.objects.extensions.directionToIcon
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.extensions.toVisibilityKeepSpace
import app.aaps.plugins.source.databinding.SourceFragmentBinding
import app.aaps.plugins.source.databinding.SourceItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BGSourceFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences

    private val disposable = CompositeDisposable()
    private val millsToThePast = T.hours(36).msecs()
    private lateinit var actionHelper: ActionModeHelper<GV>
    private var _binding: SourceFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SourceFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            actionHelper = ActionModeHelper(rh, activity, this)
            actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
            actionHelper.setOnRemoveHandler { handler -> removeSelected(handler) }
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        disposable += persistenceLayer
            .getBgReadingsDataFromTime(now - millsToThePast, false)
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> binding.recyclerview.adapter = RecyclerViewAdapter(list) }

        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({
                           disposable += persistenceLayer
                               .getBgReadingsDataFromTime(now - millsToThePast, false)
                               .observeOn(aapsSchedulers.main)
                               .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
                       }, fabricPrivacy::logException)
    }

    @Synchronized
    override fun onPause() {
        actionHelper.finish()
        disposable.clear()
        super.onPause()
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        actionHelper.onCreateOptionsMenu(menu, inflater)
        actionHelper.onPrepareOptionsMenu(menu)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    override fun onMenuItemSelected(item: MenuItem) =
        if (actionHelper.onOptionsItemSelected(item)) true
        else super.onContextItemSelected(item)

    inner class RecyclerViewAdapter internal constructor(private var glucoseValues: List<GV>) : RecyclerView.Adapter<RecyclerViewAdapter.GlucoseValuesViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): GlucoseValuesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.source_item, viewGroup, false)
            return GlucoseValuesViewHolder(v)
        }

        override fun onBindViewHolder(holder: GlucoseValuesViewHolder, position: Int) {
            val glucoseValue = glucoseValues[position]
            holder.binding.ns.visibility = (glucoseValue.ids.nightscoutId != null).toVisibilityKeepSpace()
            holder.binding.invalid.visibility = (!glucoseValue.isValid).toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDay(glucoseValue.timestamp, glucoseValues[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(glucoseValue.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeStringWithSeconds(glucoseValue.timestamp)
            holder.binding.value.text = profileUtil.fromMgdlToStringInUnits(glucoseValue.value)
            val customEnabled = preferences.get(BooleanKey.OverviewUseCustomTrendCalculator)
            val showSource = preferences.get(BooleanKey.OverviewShowSourceTrendArrow)

            holder.binding.direction.visibility = showSource.toVisibility()
            if (showSource) {
                holder.binding.direction.setImageResource(glucoseValue.trendArrow.directionToIcon())
            }

            val customArrow = if (customEnabled) calculateCustomArrow(glucoseValues, position) else TrendArrow.NONE
            if (customEnabled && customArrow != TrendArrow.NONE) {
                holder.binding.customDirection.visibility = View.VISIBLE
                holder.binding.customDirection.setImageResource(customArrow.directionToIcon())
                holder.binding.customDirection.setColorFilter(rh.gc(app.aaps.core.ui.R.color.widget_inrange))
            } else {
                holder.binding.customDirection.visibility = View.GONE
            }
            if (position > 0) {
                val previous = glucoseValues[position - 1]
                val diff = previous.timestamp - glucoseValue.timestamp
                if (diff < T.secs(20).msecs())
                    holder.binding.root.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.bgsourceError))
            }

            holder.binding.root.setOnLongClickListener {
                if (actionHelper.startRemove()) {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, glucoseValue, holder.binding.cbRemove.isChecked)
                    return@setOnLongClickListener true
                }
                false
            }
            holder.binding.root.setOnClickListener {
                if (actionHelper.isRemoving) {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, glucoseValue, holder.binding.cbRemove.isChecked)
                }
            }
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                actionHelper.updateSelection(position, glucoseValue, value)
            }
            holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            holder.binding.cbRemove.visibility = actionHelper.isRemoving.toVisibility()
        }

        override fun getItemCount() = glucoseValues.size

        inner class GlucoseValuesViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = SourceItemBinding.bind(view)
        }
    }

    private fun calculateCustomArrow(glucoseValues: List<GV>, position: Int): TrendArrow {
        val lookbackMinutes = preferences.get(IntKey.TrendCustomLookbackMinutes).coerceIn(5, 15)
        val currentReading = glucoseValues[position]
        val lookbackStartTime = currentReading.timestamp - lookbackMinutes * 60 * 1000
        val readings = mutableListOf<InMemoryGlucoseValue>()
        for (i in position until glucoseValues.size) {
            val gv = glucoseValues[i]
            if (gv.timestamp < lookbackStartTime) break
            readings.add(gv.toInMemoryGlucoseValue())
        }
        if (readings.size < 2) return TrendArrow.NONE

        val current = readings[0]
        val recentReadings = readings.filter { it.timestamp >= lookbackStartTime }
        if (recentReadings.size < 2) return TrendArrow.NONE

        val previousAverage = recentReadings.drop(1).map { it.recalculated }.average()
        val slope = if (current.timestamp == lookbackStartTime) 0.0
        else (current.recalculated - previousAverage) / (current.timestamp - lookbackStartTime)
        val slopeByMinute = slope * 60000

        return when {
            slopeByMinute <= -3.5 -> TrendArrow.DOUBLE_DOWN
            slopeByMinute <= -2   -> TrendArrow.SINGLE_DOWN
            slopeByMinute <= -1   -> TrendArrow.FORTY_FIVE_DOWN
            slopeByMinute <= 1    -> TrendArrow.FLAT
            slopeByMinute <= 2    -> TrendArrow.FORTY_FIVE_UP
            slopeByMinute <= 3.5  -> TrendArrow.SINGLE_UP
            slopeByMinute <= 40   -> TrendArrow.DOUBLE_UP
            else                  -> TrendArrow.NONE
        }
    }

    private fun GV.toInMemoryGlucoseValue(): InMemoryGlucoseValue =
        InMemoryGlucoseValue(
            timestamp = timestamp,
            value = value,
            trendArrow = TrendArrow.NONE,
            smoothed = value,
            filledGap = false,
            sourceSensor = sourceSensor
        )

    private fun getConfirmationText(selectedItems: SparseArray<GV>): String {
        if (selectedItems.size() == 1) {
            val glucoseValue = selectedItems.valueAt(0)
            return dateUtil.dateAndTimeString(glucoseValue.timestamp) + "\n" + profileUtil.fromMgdlToUnits(glucoseValue.value)
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    @SuppressLint("CheckResult")
    private fun removeSelected(selectedItems: SparseArray<GV>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                selectedItems.forEach { _, glucoseValue ->
                    disposable += persistenceLayer.invalidateGlucoseValue(
                        glucoseValue.id, action = Action.BG_REMOVED,
                        source = Sources.BgFragment, note = null,
                        listValues = listOf(ValueWithUnit.Timestamp(glucoseValue.timestamp))
                    ).subscribe()
                }
                actionHelper.finish()
            })
        }
    }
}
