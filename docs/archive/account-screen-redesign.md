# Account Screen Redesign - Functional & Technical Specification

## Overview

Redesign the account/login flow to improve first-launch UX, fix keyboard behavior, update labels, and add sign-out capability.

## Current State

- App always launches to `BookmarkListRoute` (My List)
- User must navigate: Hamburger menu → Settings → Account to sign in
- No first-launch detection or onboarding flow
- Username field labeled "Username" but the server expects an email address
- No sign-out functionality
- Keyboard IME actions not configured (defaults to Return/newline in all fields)
- URL field has no protocol prefix pre-populated

## Changes

### 1. First-Launch Redirect to Account Screen

**Behavior:** On app launch, if the user is not authenticated (no token stored), navigate directly to `AccountSettingsRoute` instead of `BookmarkListRoute`.

**Technical approach:**
- In `MainActivity.kt` / `MyDeckNavHost`, observe `SettingsDataStore.tokenFlow`
- If token is null/empty on initial composition, set `startDestination = AccountSettingsRoute`
- If token is present, keep `startDestination = BookmarkListRoute()` (current behavior)
- This avoids a new "welcome" screen and reuses the existing Account screen

**Navigation after sign-in:**
- On `AuthenticationResult.Success`, navigate to `BookmarkListRoute()` with `popUpTo(AccountSettingsRoute) { inclusive = true }` so pressing Back does not return to the login screen
- Add a new `NavigationEvent.NavigateToBookmarkList` in `AccountSettingsViewModel`
- The existing `NavigateBack` event remains for when the user reaches Account from Settings

**Back button behavior when Account is start destination:**
- Hide the back arrow in the top bar when Account is the start destination (no prior screen to return to)
- Pass a parameter or check `navController.previousBackStackEntry == null` to determine this

### 2. URL Field: Auto-populate Protocol Prefix

**Behavior:** When the URL field is empty and gains focus, pre-populate it with `https://` (or `http://` if "Allow unencrypted connections" is checked). The cursor should be placed at the end.

**Technical approach:**
- In `AccountSettingsViewModel.init`, if `url` from `settingsDataStore` is null/blank, set `url = "https://"` in the UI state
- When `onAllowUnencryptedConnectionChanged` toggles:
  - If the current URL starts with `https://` and the user enables unencrypted, leave it as-is (https still works)
  - If the current URL starts with `http://` and the user disables unencrypted, replace prefix with `https://`
  - If the URL is exactly `https://` or `http://` (just the prefix), swap to match the setting

**Focus:** Request focus on the URL field when the screen opens. Use `FocusRequester` with `LaunchedEffect` to request focus after composition.

### 3. Keyboard IME Actions

**Behavior:** Each field's keyboard should show "Next" to tab to the next field, except the last field (Password) which should show "Done" or "Go" to submit.

**Technical approach:**
- URL field: `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)`, `keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })`
- Email field: `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)`, `keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })`
- Password field: `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)`, `keyboardActions = KeyboardActions(onDone = { keyboardController?.hide(); if (loginEnabled) onLoginClicked() })`

This means pressing the IME action on the password field will submit the form if all fields are valid. If fields are incomplete, pressing Done just dismisses the keyboard (the Login button remains disabled as visual feedback).

**Alternative considered:** Always use `ImeAction.Next` and move focus to the Login button from the password field. Rejected because focusing a button from keyboard is non-standard on Android and can confuse users. `Done` that triggers submit is the standard Android pattern.

### 4. Fix Return Key Inserting Characters

This is a direct consequence of not setting `singleLine = true` on the text fields. The `OutlinedTextField` defaults to multiline, so Return inserts a newline.

**Fix:** Add `singleLine = true` to all three `OutlinedTextField` composables (URL, Email, Password). This also automatically maps the Return key to the IME action.

### 5. Rename "Username" to "Email Address"

**Changes:**
- Update string resource `account_settings_username_label` from "Username" to "Email Address"
- Update string resource `account_settings_username_placeholder` from "Username" to "email@example.com" (or similar)
- Update string resource `account_settings_username_error` text if it references "username"
- In `SettingsScreen.kt`, the subtitle under Account currently shows the username — update the label contextually if needed (the value itself is fine since it's already the email)

**Files to modify:**
- `app/src/main/res/values/strings.xml` (and any localized variants)
- No Kotlin code changes needed beyond string resources

### 6. Sign-Out Button and Section

**Behavior:**
- Show a "Sign Out" section **only when the user is authenticated** (token exists)
- Position it below the login form fields, separated by a divider
- Include explanatory text: "Signing out will clear all local data including saved bookmarks."
- The sign-in fields and Login button remain visible below the sign-out section, allowing the user to sign into a different server/account without signing out first
- On sign-out: clear credentials, clear local bookmark database, navigate to a fresh Account screen state

**Layout order (when signed in):**
1. URL field (pre-filled with current server)
2. Email field (pre-filled with current email)
3. Password field (pre-filled with current password)
4. Allow unencrypted connections checkbox
5. Login button (allows re-login or login to different server)
6. `HorizontalDivider` with vertical spacing
7. Sign-out section:
   - Text: "Signing out will clear all local data including saved bookmarks."
   - "Sign Out" button (Material3 `OutlinedButton` or `TextButton` with warning color)

**Technical approach:**
- Add `isLoggedIn: Boolean` to `AccountSettingsUiState`, derived from `UserRepository.observeIsLoggedIn()` in the ViewModel's `init`
- Add `onSignOut` callback and `signOut()` function in `AccountSettingsViewModel`:
  - Call `settingsDataStore.clearCredentials()`
  - Clear bookmark database (reuse existing logic from `AuthenticateUseCase` or add a `SignOutUseCase`)
  - Cancel any active sync workers
  - Update UI state: clear fields, set `isLoggedIn = false`
  - If Account screen is the full-screen view (not navigated from Settings), stay on Account screen
  - If navigated from Settings, navigate back to Settings or stay on Account
- Add confirmation dialog before sign-out (Material3 `AlertDialog`): "Sign out? This will clear all local data." with Cancel / Sign Out actions

### 7. Files to Modify

| File | Changes |
|------|---------|
| `MainActivity.kt` | Conditional start destination based on auth state |
| `AccountSettingsScreen.kt` | Add `singleLine`, IME actions, focus management, sign-out section, conditional back button |
| `AccountSettingsViewModel.kt` | URL prefix logic, sign-out function, `isLoggedIn` state, `NavigateToBookmarkList` event, focus state |
| `Routes.kt` | No changes needed |
| `strings.xml` | Rename Username → Email Address, add sign-out strings |
| `SettingsDataStore.kt` / `SettingsDataStoreImpl.kt` | Possibly add `clearAllData()` if `clearCredentials()` is insufficient |
| New: `SignOutUseCase.kt` (optional) | Orchestrate credential clearing + database clearing + worker cancellation |

### 8. Edge Cases

- **Deep link to Account from notification (401):** Already handled via `navigateToAccountSettings` intent extra. Post-redesign this continues to work since AccountSettingsRoute is still registered.
- **Share intent while logged out:** The share flow navigates to BookmarkListRoute which will fail to save. Consider: if not logged in and a share intent arrives, redirect to Account screen with a message, then process the share after login. (Out of scope for this spec but worth noting.)
- **Token expiry:** Not addressed here. The existing 401 interceptor handles this separately.
- **Back press on Account as start destination:** With `startDestination = AccountSettingsRoute` and no back stack, pressing Back should exit the app (default Android behavior). This is correct.

### 9. Testing Notes

- Verify first launch goes to Account screen
- Verify login navigates to My List and back button does not return to Account
- Verify URL pre-populates with `https://`
- Verify keyboard shows Next/Done appropriately and Return does not insert characters
- Verify sign-out clears data and shows fresh Account screen
- Verify navigating from Settings → Account still shows back button and works normally
