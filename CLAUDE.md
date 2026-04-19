# Claude Code — Dobbscoin Android Wallet Upgrade

## Project
Android wallet app for Dobbscoin (BOB) cryptocurrency. Goal: make it build and
install cleanly on Android 16 (API 36) without altering any consensus-critical code.

## HARD RULES — DO NOT MODIFY UNDER ANY CIRCUMSTANCES
1. Consensus rules
2. Blockchain data format
3. Transaction serialization
4. Script / signature validation logic
5. Network protocol messages
6. Wallet key / seed generation format
7. RPC method behavior used by existing services
8. Database schema (without explicit user approval)
9. Existing wallet addresses or balances
10. Anything that could fork or invalidate the chain

## ALLOWED CHANGES (build/compat layer only)
- build.gradle / settings.gradle / gradle-wrapper.properties
- compileSdk / targetSdk / minSdk version bumps
- Dependency version upgrades (non-consensus libraries only)
- AndroidX migration (android.support.* → androidx.*)
- Deprecated API replacements (UI, permissions, lifecycle only)
- AndroidManifest.xml — only for permissions / exported flags
- ProGuard / R8 rules
- Java/Kotlin version alignment

## WORKFLOW
- Always explain what you're changing and why before editing
- If a change touches anything near consensus code, STOP and ask the user
- Build with: cd ~/claude/androidbob && ./gradlew assembleDebug
- Fix one layer of errors at a time; re-build after each fix
- Never auto-commit; leave commits to the user
