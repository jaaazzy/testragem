# UI components

Local shadcn/ui components, based on the New York registry:
https://github.com/shadcn-ui/ui/tree/main/apps/v4/registry/new-york-v4/ui

Button and Badge use class-variance-authority; Button uses Radix Slot.
Tabs use Radix Tabs for keyboard navigation, focus management, and ARIA relationships.
Native Select keeps platform dropdown behavior. Label and Separator use their native HTML semantics.
Components use project color tokens and shared class merging, with Lucide icons.
Skeleton is adapted from the shadcn registry and used while session checks and workspace page chunks load.
The appearance toggle uses the shadcn Button with native toggle-button semantics (`aria-pressed`) and an animated sun/moon thumb. It needs no additional primitive dependency. Only light and dark choices are exposed; before a choice is saved, appearance follows the device.
Card titles use semantic headings; alerts provide persistent inline feedback.

See SHADCN-LICENSE for upstream licensing.
