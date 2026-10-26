# Screen Input Field Specifications

> **Project**: SAGASU / TimDo  
> **Document Type**: UI Input & Action Field Specification  
> **Scope**: Actual input fields, dropdowns, checkboxes, file uploads, textareas, and action buttons found in the source code.

---

## 1. Home Page (`/`)

**Functionality**: Landing page of the website. It displays an introduction banner with an AI radar matching demonstration, an interactive map preview, the 3-step item recovery process, and buttons to create lost/found posts or search for items.

> No user input fields. Page contains only navigation links and CTA buttons.

---

## 2. Login Page (`/dang-nhap`)

**Functionality**: Login page for users to sign into the system with their username/email and password, or sign in using a Google account.

| Field Name | Description |
| :--- | :--- |
| Identifier | **Text Input**: Enter username or email. |
| Password | **Password Input**: Enter password; toggle button to show or hide characters. |
| Remember Me | **Checkbox**: Keep the session saved on this device. |
| Login Button | **Button**: Submits credentials to sign in. |
| Google Sign-In | **Button**: Signs in with a Google account. |

---

## 3. Register Page (`/dang-ky`)

**Functionality**: Registration page for new users to create an account by filling in personal details, phone number, email, and password, or signing up using Google.

| Field Name | Description |
| :--- | :--- |
| Full Name | **Text Input**: Enter full name. |
| Username | **Text Input**: Enter username. |
| Phone Number | **Tel / Text Input**: Enter phone number. |
| Email | **Email Input**: Enter contact email. |
| Password | **Password Input**: Enter password (minimum 8 characters); has show/hide toggle. |
| Confirm Password | **Password Input**: Re-enter password to verify. |
| Terms Agreement | **Checkbox**: Agree to platform terms of use and privacy policy. |
| Register Button | **Button**: Submits the form to create the account. |
| Google Sign-Up | **Button**: Registers with a Google account. |

---

## 4. Post Item Page (`/dang-tin`, `/dang-tin-nhat-duoc`)

**Functionality**: A 3-step form to create a lost or found post: Step 0 uploads item photos, Step 1 enters item details and location, and Step 2 previews the post before publishing.

### Step 0 — Images

| Field Name | Description |
| :--- | :--- |
| Post Type | **Toggle Button**: Selects between "Lost" (Cần tìm đồ mất) and "Found" (Đã nhặt được đồ). |
| Item Images | **File Upload**: Uploads up to 5 photos of the item (JPG, PNG). |
| Remove Image | **Button**: Removes an uploaded photo from the preview list. |

### Step 1 — Details

| Field Name | Description |
| :--- | :--- |
| Item Name | **Text Input**: Enter item title. |
| Incident Date | **Date Picker**: Select the date the item was lost or found. |
| Incident Time | **Time Picker**: Select the approximate time. |
| Location | **Text Input**: Enter the street, building, or address. |
| Item Description | **Textarea**: Enter identifying details (color, brand, serial number, unique marks). |

### Step 2 — Review & Submit

| Field Name | Description |
| :--- | :--- |
| Publish Only Button | **Button**: Publishes the post normally without AI matching. |
| Publish & AI Scan Button | **Button**: Publishes the post and navigates to the AI Smart Match page. |

---

## 5. Search Page (`/tim-kiem`)

**Functionality**: Search page to find lost and found items by keyword, category, time period, and city.

| Field Name | Description |
| :--- | :--- |
| Search Keyword | **Text Input**: Enter keyword to search by item name or location. |
| Clear Keyword | **Button**: Clears the search input. |
| Category | **Dropdown / Select**: Filters results by category (All, Wallets & Documents, Electronics, Keys, Pets, Others). |
| Search Submit | **Button**: Submits the search query. |
| Time Filter | **Radio Group**: Filters results by time (Anytime, Past 24 hours, Past 7 days, Custom). |
| Region Filter | **Checkbox Group**: Filters results by city (Hanoi, Ho Chi Minh City, Others). |
| Apply Filter | **Button**: Applies the sidebar filters to results. |

---

## 6. Item Detail Page (`/chi-tiet/:id`)

**Functionality**: Displays the details of a single post, including photos, item description, location, time, poster contact card, and AI match similarity (if matched).

> No user input fields. Contains display data and action buttons only.

| Field Name | Description |
| :--- | :--- |
| Activate AI Smart Match | **Button**: Runs AI matching scan (visible to post owner only). |
| Claim Ownership | **Button**: Opens the claim verification page (`/xac-minh/:id`). |
| Share | **Button**: Copies or shares the post link. |
| Report Post | **Button**: Flags or reports the post. |
| View Profile | **Button**: Views the poster's profile. |

---

## 7. AI Smart Match Results Page (`/smart-match`)

**Functionality**: Shows matching results found by AI for a posted item, displaying matching scores, photo comparison, and match breakdowns (visual, location, time).

> No user input fields. Displays read-only match cards.

| Field Name | Description |
| :--- | :--- |
| View Detail & Claim | **Button**: Opens the Item Detail page for the matched item. |
| Return to Home | **Button**: Navigates back to the homepage. |

---

## 8. Claim / Proof of Ownership Page (`/xac-minh/:id`)

**Functionality**: Verification page where a claimant submits private details and an optional proof photo to the finder to prove they own the item.

| Field Name | Description |
| :--- | :--- |
| Secret Identifying Detail | **Textarea**: Required; enter a secret detail only the owner knows (e.g., cash inside, card name, scratch location). |
| Proof Image | **File Upload**: Optional photo proof (past photo with item, bill, receipt). |
| Remove Proof Image | **Button**: Removes the uploaded proof photo. |
| Contact Info | **Read-only Text Input**: Pre-filled phone number from user profile. |
| Meeting Preference | **Text Input**: Enter preferred time and location to meet. |
| Submit Claim | **Button**: Sends the claim request to the finder. |

---

## 9. Profile Page (`/ho-so`)

**Functionality**: Personal profile page showing user details, post statistics, tabs for user posts, activity log, and security settings, with a sidebar to edit profile info.

### Profile Header

| Field Name | Description |
| :--- | :--- |
| Edit Profile Toggle | **Button**: Toggles the edit form in the sidebar. |
| Change Avatar | **Button / File Upload**: Changes the profile avatar. |

### Posts Tab

| Field Name | Description |
| :--- | :--- |
| Post Status Filter | **Chip / Button Group**: Filters user posts (All, Lost, Found, Resolved). |

### Security Tab

| Field Name | Description |
| :--- | :--- |
| Two-Factor Auth (2FA) | **Toggle Switch**: Turns two-factor authentication on or off. |
| Email Notifications | **Toggle Switch**: Turns email notifications on or off. |
| Push Notifications | **Toggle Switch**: Turns browser push notifications on or off. |
| Delete Account | **Button**: Permanently deletes the account. |

### Sidebar — Edit Profile Form

| Field Name | Description |
| :--- | :--- |
| Full Name | **Text Input**: Edits user's full name. |
| Email | **Email Input**: Edits user's email address. |
| Phone | **Tel / Text Input**: Edits user's phone number. |
| Occupation | **Text Input**: Edits user's job or title. |
| Save Changes | **Button**: Saves updated profile information. |
| Cancel | **Button**: Cancels editing and closes the edit form. |

---

## 10. My Posts Management Page (`/bai-dang-cua-toi`)

**Functionality**: Management page for users to view and filter all their posts in a table, search posts by title, or mark items as resolved.

| Field Name | Description |
| :--- | :--- |
| Status Filter | **Chip / Button Group**: Filters table by status (All, Active, Discussing, Resolved, Lost, Found). |
| Search by Name | **Text Input**: Filters the post list by title in real-time. |
| New Post Button | **Button**: Navigates to the Post Item page (`/dang-tin`). |
| View Detail | **Button / Row Action**: Opens the item detail page. |
| Mark as Resolved | **Button / Row Action**: Updates post status to resolved. |
| Delete Post | **Button / Row Action**: Deletes the post. |

---

## 11. Admin Overview / Dashboard (`/admin`)

**Functionality**: Admin dashboard page showing summary cards for system statistics, a list of pending reports, and recent post listings.

> No user input fields. Displays read-only stats and links to management pages.

---

## 12. Admin Items Management Page (`/admin/items`)

**Functionality**: Admin page to inspect and moderate all lost and found posts across the system, with filters by type, location, and status, and an action modal to hide or approve items.

### Filter Bar

| Field Name | Description |
| :--- | :--- |
| Type Filter | **Tab / Button Group**: Filters items by type (All, Lost, Found). |
| Search Items | **Text Input**: Searches by item name, post ID, poster name, or phone number. |
| Clear Search | **Button**: Clears the current search input. |
| Location Filter | **Dropdown / Select**: Filters by city (All, Hanoi, Ho Chi Minh City). |
| Status Filter | **Dropdown / Select**: Filters by status (All, Active, AI Matched, Resolved, Hidden). |
| Reset Filters | **Button**: Clears all filters back to defaults. |

### Item Action Modal

| Field Name | Description |
| :--- | :--- |
| Action Reason | **Textarea**: Required reason when hiding or approving an item. |
| Cancel | **Button**: Closes the modal without changes. |
| Confirm Action | **Button**: Confirms the hide or approve action. |

---

## 13. Admin Reports & Disputes Page (`/admin/reports`)

**Functionality**: Admin page to manage user reports and disputes (fraud claims, extortion, ownership disputes), inspect case details and evidence, and take actions to resolve, warn, ban, or dismiss cases.

### Report List View

| Field Name | Description |
| :--- | :--- |
| Report Type Filter | **Tab / Button Group**: Filters reports by status (Pending, Fraud Claim, Extortion, Dispute, Resolved, All). |
| Select Report | **Clickable Row**: Clicks a table row to view report details. |

### Report Detail View

| Field Name | Description |
| :--- | :--- |
| Back to List | **Button**: Returns to the report list. |
| Resolution Reason | **Textarea**: Enter the reason for the decision (required; disabled after resolution). |
| Dismiss Report | **Button**: Dismisses the report as invalid. |
| Resolved / Issue Warning | **Button**: Resolves the report and issues a user warning. |
| Ban Account & Resolve | **Button**: Suspends the reported user's account and resolves the case. |
| Reopen Case | **Button**: Reopens a resolved case. |

---

## 14. Admin Users Management Page (`/admin/users`)

**Functionality**: Admin page to view and manage user accounts, with filters by account status, search by keyword, a modal to create or edit accounts, and a modal to warn or ban users.

### Filter Bar

| Field Name | Description |
| :--- | :--- |
| Status Filter | **Tab / Button Group**: Filters users by status (All, Active, Banned, Needs Attention). |
| Search Users | **Text Input**: Searches by name, email, or user ID. |
| Clear Search | **Button**: Clears the current search input. |
| Add User | **Button**: Opens the Create User modal. |

### User CRUD Modal (Create / Edit)

| Field Name | Description |
| :--- | :--- |
| Full Name | **Text Input**: Required; user's full name. |
| Email | **Email Input**: Required; user's email address. |
| Role | **Dropdown / Select**: Select role (Member, Active Member, Warning, Admin). |
| Trust Score | **Number Input**: Enter trust score from 0 to 100. |
| Account Status | **Dropdown / Select**: Select status (Active, Suspended). |
| Cancel | **Button**: Closes the modal without saving. |
| Save / Create Button | **Button**: Saves the new or edited user account. |

### User Action Modal (Warning / Ban)

| Field Name | Description |
| :--- | :--- |
| Action Reason | **Textarea**: Required reason for issuing a warning or ban. |
| Cancel | **Button**: Closes the modal without action. |
| Confirm | **Button**: Confirms the warning or ban. |

---

## 15. Admin User Details Page (`/admin/users/:id`)

**Functionality**: Admin detail page showing an individual user's profile, trust score, post history, and violation reports, with buttons to send notifications or toggle account suspension.

| Field Name | Description |
| :--- | :--- |
| Back to Users List | **Button**: Navigates back to the users list. |
| Send Warning | **Button**: Opens modal to send a notice or warning to this user. |
| Ban / Unban Account | **Button**: Toggles account suspension. |
| Profile & Trust Tab | **Tab / Button**: Shows user profile details and trust score breakdown. |
| Post History Tab | **Tab / Button**: Shows items posted by this user. |
| Violation History Tab | **Tab / Button**: Shows violation reports related to this user. |
