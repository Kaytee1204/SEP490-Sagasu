# Feature organization

The P2P features maintained on `datpq` follow this structure. Create a directory
only when the feature needs it; do not add empty folders or forwarding index files.

```text
features/<feature>/
  api/          # HTTP requests and multipart uploads
  pages/        # Screens mounted by the router
  components/   # UI, panels, dialogs and compatibility redirects
  hooks/        # React hooks, named useSomething
  context/      # Context definitions and providers
  constants/    # Shared labels and fixed values
  utils/        # Formatting, validation and other helper functions
  storage/      # Browser persistence, such as IndexedDB drafts
  styles/       # CSS shared by several files in this feature
```

- Use PascalCase for page and component filenames, camelCase for JavaScript
  helpers, and `use` prefixes for hook filenames.
- Keep CSS used by a single page or component beside that file. Put CSS shared
  across features in `src/styles`; it does not belong to a placeholder feature.
- Use relative imports within a feature and `@/features/...` when importing from
  another feature. Group related components in subdirectories when useful.
- Keep API calls out of `utils`, and browser draft persistence out of `api`.
- Keep reusable components out of `pages`: the page and dialog can both import a
  shared editor component. Keep independently routed screens in separate files.
- Group by business responsibility: post creation, editing and personal post
  management belong to `items`; account details and security belong to `profile`.

## Current P2P ownership

| Feature | Responsibility |
| --- | --- |
| `items` | Post creation, detail, editing, personal posts and draft storage |
| `claims` | P2P ownership requests, questionnaire answers and review |
| `chat` | P2P conversations, attachments, location sharing and realtime state |
| `handover` | P2P handover recording, receipt confirmation and issue reporting |
| `reports` | User report creation, reasons, personal report list and detail |

Office reception and Manager investigation remain in `office`, `receipt`,
`approver` and `report-review`. Shared imports may refer to the P2P features above.
