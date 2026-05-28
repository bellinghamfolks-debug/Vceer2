# v1.13.0 — keep entry points reachable by reflection / framework.
# AccessibilityService is bound by the OS; MainActivity is launched
# from the launcher; BotConfig/StatusListener are constructed and
# referenced from MainActivity across the Service boundary.

-keep class com.mokafeefah.clicker.ClickerService { *; }
-keep class com.mokafeefah.clicker.MainActivity { *; }
-keep class com.mokafeefah.clicker.ClickerService$BotConfig { *; }
-keep class com.mokafeefah.clicker.ClickerService$StatusListener { *; }
-keep class com.mokafeefah.clicker.LikedMembersDb { *; }

# androidx.appcompat + material reflection that R8 sometimes can't see.
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# Keep Accessibility nodes that frameworks expect.
-keep class android.view.accessibility.** { *; }
