package tv.projectivy.plugin.wallpaperprovider.sample

import android.os.Bundle
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import io.makeran218.projectivy.tvbgsuite.wallpaper.R

class SettingsFragment : GuidedStepSupportFragment() {

    companion object {
        private const val TAG = "SettingsFragment"
        private const val ACTION_ID_SERVER_URL = 1L
        private const val ACTION_ID_EVENT_IDLE = 9L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance(
            getString(R.string.plugin_name),
            "Metadata-Driven Provider Configuration",
            getString(R.string.settings),
            AppCompatResources.getDrawable(requireActivity(), R.drawable.ic_plugin)
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        try {
            PreferencesManager.init(requireContext())

            val serverUrl = PreferencesManager.serverUrl
            val refreshOnIdle = PreferencesManager.refreshOnIdleExit

            // 1. Server URL Action
            actions.add(GuidedAction.Builder(context)
                .id(ACTION_ID_SERVER_URL)
                .title("Github Page URL")
                .description(if (serverUrl.isNotEmpty()) serverUrl else "https://...")
                .editDescription(serverUrl)
                .descriptionEditable(true)
                .build())

            // 2. Refresh on Idle Toggle
            actions.add(GuidedAction.Builder(context)
                .id(ACTION_ID_EVENT_IDLE)
                .title("Refresh on idle exit")
                .description("Fetch new background when returning from idle")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(refreshOnIdle)
                .build())

        } catch (e: Exception) {
            Log.e(TAG, "Error creating actions", e)
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_ID_EVENT_IDLE -> {
                val newState = !PreferencesManager.refreshOnIdleExit
                PreferencesManager.refreshOnIdleExit = newState
                action.isChecked = newState
                notifySettingsChanged()
            }
        }
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        if (action.id == ACTION_ID_SERVER_URL) {
            val newUrl = action.editDescription.toString()
            PreferencesManager.serverUrl = newUrl
            action.description = newUrl

            // Notify UI to refresh the description
            val position = findActionPositionById(ACTION_ID_SERVER_URL)
            if (position != -1) notifyActionChanged(position)

            notifySettingsChanged()
        }
        return GuidedAction.ACTION_ID_CURRENT
    }

    private fun notifySettingsChanged() {
        (requireActivity() as? SettingsActivity)?.requestWallpaperUpdate()
    }
}