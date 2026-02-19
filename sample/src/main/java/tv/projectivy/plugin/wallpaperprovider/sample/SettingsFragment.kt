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
        private const val ACTION_ID_APP_TARGET = 10L
    }

    override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
        // Check if the clicked sub-action belongs to our Target App group
        if (action.checkSetId == 10) {
            val newTarget = action.title.toString()

            // 1. Save to Preferences
            PreferencesManager.appTarget = newTarget

            // 2. Update the main UI to show the new selection in the description
            val parentAction = findActionById(ACTION_ID_APP_TARGET)
            parentAction?.description = newTarget

            // 3. Refresh the UI so the user sees the checkmark move
            notifyActionChanged(findActionPositionById(ACTION_ID_APP_TARGET))

            // 4. Trigger a wallpaper update to apply the new link format
            (activity as? SettingsActivity)?.requestWallpaperUpdate()

            // Close the sub-menu and go back to the main settings list
            return true
        }
        return super.onSubGuidedActionClicked(action)
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
            val APP_TARGETS = listOf("Stremio", "Kodi", "Plex", "Emby")

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


            actions.add(GuidedAction.Builder(context)
                .id(ACTION_ID_APP_TARGET)
                .title("Target App")
                .description(PreferencesManager.appTarget) // Shows current choice
                .subActions(APP_TARGETS.mapIndexed { i, name ->
                    GuidedAction.Builder(context)
                        .id(1000L + i)
                        .title(name)
                        .checkSetId(10) // Radio button group ID
                        .checked(name == PreferencesManager.appTarget) // Check the current one
                        .build()
                })
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