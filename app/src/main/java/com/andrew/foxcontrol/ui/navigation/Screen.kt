package com.andrew.foxcontrol.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen(ONBOARDING)
    object Home : Screen(HOME)
    object Settings : Screen(SETTINGS)
    object PrivateSettings : Screen(PRIVATE_SETTINGS)
    object AppDetail : Screen(APP_DETAIL) {
        const val ARG_PACKAGE_NAME = "packageName"
        /** Route pattern registered in the NavHost. */
        const val ROUTE_PATTERN = "$APP_DETAIL/{$ARG_PACKAGE_NAME}"
        fun createRoute(packageName: String) = "$APP_DETAIL/$packageName"
    }
    object EmailSettings : Screen(EMAIL_SETTINGS)
    object EmailRecipients : Screen(EMAIL_RECIPIENTS)
    object VendorInstructions : Screen(VENDOR_INSTRUCTIONS)
    object Debug : Screen(DEBUG)

    companion object {
        const val ONBOARDING = "onboarding"
        const val HOME = "home"
        const val SETTINGS = "settings"
        const val PRIVATE_SETTINGS = "private_settings"
        const val APP_DETAIL = "app_detail"
        const val EMAIL_SETTINGS = "email_settings"
        const val EMAIL_RECIPIENTS = "email_recipients"
        const val VENDOR_INSTRUCTIONS = "vendor_instructions"
        const val DEBUG = "debug"
    }
}
