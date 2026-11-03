# Walkthrough - Backer App for Amazon Flex Call & Navigation

I have implemented the complete Android background service and companion UI for the **Backer** app, designed specifically to integrate seamlessly with **Amazon Flex** (`com.amazon.flex.rabbit`) and phone call activities.

## Changes Made

### 1. Accessibility Service (`[FlexAccessibilityService.kt](file:///C:/Users/adars/AndroidStudioProjects/backer/app/src/main/java/com/pluto/backer/FlexAccessibilityService.kt)`)
- **Background Event Monitoring**: Listens to window state change events specifically for Amazon Flex (`com.amazon.flex.rabbit`) and phone dialer packages (`com.google.android.dialer`, etc.).
- **1.5s Auto-Return to Foreground**: When a phone call screen opens while using Amazon Flex, a 1.5-second (`1500ms`) timer schedules bringing Amazon Flex back to the foreground (`Intent.FLAG_ACTIVITY_NEW_TASK`), leaving the phone call running in the background.
- **Back-to-Map Navigation**: Intercepts back key events and window transitions, automatically performing `GLOBAL_ACTION_BACK` iteratively (every 300ms) until `NavigationActivity` (recognized by toolbar title `"TRAVEL"` or map container views) is reached.

### 2. Accessibility Service Config (`[accessibility_service_config.xml](file:///C:/Users/adars/AndroidStudioProjects/backer/app/src/main/res/xml/accessibility_service_config.xml)`)
- Configured to filter only relevant packages (`com.amazon.flex.rabbit` and dialer apps) to ensure maximum battery efficiency (event-driven with zero background polling overhead).

### 3. Companion UI & Status (`[MainActivity.kt](file:///C:/Users/adars/AndroidStudioProjects/backer/app/src/main/java/com/pluto/backer/MainActivity.kt)` & `[activity_main.xml](file:///C:/Users/adars/AndroidStudioProjects/backer/app/src/main/res/layout/activity_main.xml)`)
- Provides a simple user interface showing whether the Accessibility Service is enabled or disabled, with a direct button to open Android Accessibility Settings.

## Verification
- **Gradle Build**: Successfully built and compiled (`app:assembleDebug`) with 0 errors.
