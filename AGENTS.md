# Rasven project instructions

## Persian layout is always right-to-left

- Treat Persian (`fa`) screens and components as RTL by default. This applies to the whole text field: label, helper text, placeholder, entered value, selection, and the initial caret position. Persian input must begin at the right edge, never centered or left-aligned.
- Set RTL behavior explicitly in shared components so it does not depend on whether a screen happens to provide an RTL composition locally.
- Technical values such as phone numbers, addresses, server names, ports, and passwords may keep LTR text direction when their content requires it. Their visual alignment must still follow the Figma frame and the Persian field contract unless Figma explicitly specifies otherwise.
- Keep RTL changes scoped to the relevant component or screen. Do not reverse whole-screen rows, icon order, navigation, or absolute positions without checking the corresponding Figma frame.
- English (`en`) content remains LTR. Any shared component supporting both languages must expose a clear direction/alignment contract rather than infer it from glyphs alone.
- Shared Persian text inputs must use an RTL paragraph fallback for empty content/caret placement (`ContentOrRtl` or equivalent). Technical LTR islands must opt in explicitly; do not make Persian fields inherit an LTR fallback just because the surrounding geometry container is LTR.
- Pixel-based RTL tests must isolate text/glyph pixels from the field surface/background (for example with a same-state differential capture). An alpha-only scan of a composited node is not valid evidence of glyph position.

## Figma fidelity and delivery audit

- Use the linked Figma frame as the source of truth for geometry, content order, spacing, typography, and component states. Compare all affected states after changing a shared component.
- Before delivery, audit every affected call site and dependent screen, including existing behavior that could change through shared code. Check default and explicit overrides, empty and filled fields, errors, disabled/read-only states, and Persian/English direction behavior where present.
- Run the relevant unit tests, Android instrumentation tests, and build tasks. Review the final diff and report any visual or device checks that could not be run; do not label an unrun check as passed.
- Every progress or delivery message must include the exact Gradle command(s) used and a concise test/build status with counts when available. Clearly mark tests that were not run or could not reach project compilation.
