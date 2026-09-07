# Approved asset fixtures

These are fixed authored export snapshots for regression comparisons, not runtime-generated files.
- light_collector: selected 86-cube user export from 2026-09-06.
- tower_workbench: approved source geometry and texture before block-local conversion.
- asc_adapter: six imported gun display definitions used to check authored scales; does not prove runtime pack availability.

Update snapshots deliberately when approving new assets. Do not regenerate them from the implementation under test. No test should depend on ignored run/ or source_assets/ directories.
