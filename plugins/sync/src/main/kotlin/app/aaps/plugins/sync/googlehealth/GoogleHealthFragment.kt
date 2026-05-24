package app.aaps.plugins.sync.googlehealth

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.databinding.GooglehealthFragmentBinding
import app.aaps.plugins.sync.googlehealth.events.EventGoogleHealthUpdateGUI
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.launch
import javax.inject.Inject

class GoogleHealthFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var googleHealthPlugin: GoogleHealthPlugin

    companion object {
        const val ID_MENU_CLEAR_LOG = 601
        const val ID_MENU_FULL_SYNC = 602

        val PERMISSIONS = setOf(
            HealthPermission.getWritePermission(BloodGlucoseRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
    }

    override var plugin: PluginBase? = null

    private val disposable = CompositeDisposable()
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var hadMissingPermissions = false

    private var _binding: GooglehealthFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        GooglehealthFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
            it.grantPermissions.setOnClickListener { requestHealthConnectPermissions() }
        }.root

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clear_log)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_CLEAR_LOG -> {
                googleHealthPlugin.clearLog()
                true
            }
            ID_MENU_FULL_SYNC -> {
                context?.let { ctx ->
                    OKDialog.showConfirmation(
                        ctx,
                        rh.gs(R.string.google_health),
                        rh.gs(R.string.full_google_health_sync_comment),
                        Runnable { handler.post { googleHealthPlugin.triggerFullSync() } }
                    )
                }
                true
            }
            else -> false
        }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventGoogleHealthUpdateGUI::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
        checkAndUpdatePermissionButton(triggerSyncIfJustGranted = hadMissingPermissions)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateGui() {
        if (_binding == null) return
        binding.log.text = googleHealthPlugin.textLog()
    }

    private fun requestHealthConnectPermissions() {
        try {
            val contract = PermissionController.createRequestPermissionResultContract()
            val intent = contract.createIntent(requireContext(), PERMISSIONS)
            hadMissingPermissions = true
            startActivity(intent)
        } catch (e: Exception) {
            aapsLogger.error("GoogleHealthFragment", "Cannot open Health Connect permissions: ${e.message}")
            try {
                startActivity(android.content.Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            } catch (e2: Exception) {
                aapsLogger.error("GoogleHealthFragment", "Cannot open Health Connect settings: ${e2.message}")
            }
        }
    }

    private fun checkAndUpdatePermissionButton(triggerSyncIfJustGranted: Boolean = false) {
        val ctx = context ?: return
        if (HealthConnectClient.getSdkStatus(ctx) != HealthConnectClient.SDK_AVAILABLE) {
            _binding?.grantPermissions?.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(ctx)
                val granted = client.permissionController.getGrantedPermissions()
                val hasAll = granted.containsAll(PERMISSIONS)
                _binding?.grantPermissions?.visibility = if (hasAll) View.GONE else View.VISIBLE
                if (hasAll && triggerSyncIfJustGranted) {
                    hadMissingPermissions = false
                    googleHealthPlugin.triggerFullSync()
                }
            } catch (e: Exception) {
                _binding?.grantPermissions?.visibility = View.VISIBLE
            }
        }
    }
}
