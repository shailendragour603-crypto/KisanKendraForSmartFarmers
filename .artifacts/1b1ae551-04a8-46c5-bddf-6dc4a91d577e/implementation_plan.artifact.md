# UI & Theming Redesign Plan - High Contrast & Clean Aesthetics

Refactor the app's color palette, card backgrounds, chip colors, titles, labels, and placeholders across all pages and functions in both light and dark modes to eliminate low-contrast combinations (such as green text on light green background and white text on light green background) and ensure a professional, highly legible visual appearance.

## User Review Required

> [!IMPORTANT]
> - **Card Backgrounds**: Replacing light green card backgrounds (`#E8F5E9`, `#F1F8E9`) with clean White (`#FFFFFF`) in light mode and Dark Surface (`#1E1E1E` / `#252525`) in dark mode to ensure high text contrast.
> - **Text & Placeholder Colors**: Standardizing text colors (`?android:attr/textColorPrimary`, `#212121`, `#1A1A1A` in light mode; `#FFFFFF`, `#E0E0E0` in dark mode) and placeholder/hint colors (`#757575` in light mode, `#B0BEC5` in dark mode).
> - **Chips & Badges**: Clean neutral/gray backgrounds with high-contrast text instead of tinted green cards for general items.

## Open Questions

None. Proceeding with clean Material 3 design principles with high contrast and accessibility.

## Proposed Changes

### Theme & Colors
#### [MODIFY] [colors.xml (Light)](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/values/colors.xml)
#### [MODIFY] [colors.xml (Dark)](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/values-night/colors.xml)

### Layouts & UI Components (All Pages & Functions)
#### [MODIFY] [fragment_home.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/fragment_home.xml)
#### [MODIFY] [fragment_forum.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/fragment_forum.xml)
#### [MODIFY] [fragment_mandi.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/fragment_mandi.xml)
#### [MODIFY] [fragment_weather.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/fragment_weather.xml)
#### [MODIFY] [fragment_news.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/fragment_news.xml)
#### [MODIFY] [activity_sign_in.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/activity_sign_in.xml)
#### [MODIFY] [activity_settings.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/activity_settings.xml)
#### [MODIFY] [bottom_sheet_create_forum_post.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/bottom_sheet_create_forum_post.xml)
#### [MODIFY] [bottom_sheet_price_chart.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/bottom_sheet_price_chart.xml)
#### [MODIFY] [dialog_create_forum_post.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/dialog_create_forum_post.xml)
#### [MODIFY] [dialog_add_crop_cycle.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/dialog_add_crop_cycle.xml)
#### [MODIFY] [dialog_add_khata_entry.xml](file:///C:/Users/shail/KisanKendraForSmartFarmers/app/src/main/res/layout/dialog_add_khata_entry.xml)

## Verification Plan

### Automated Tests
- Run Gradle build (`app:assembleDebug`) to verify compilation and resource validity.

### Manual Verification
- Deploy app or inspect UI themes in light and dark mode across fragments and dialogs.
