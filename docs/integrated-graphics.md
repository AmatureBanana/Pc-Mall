# CPU Integrated Graphics Parameters

Verified on 2026-11-08 against manufacturer specifications. `HardwareParameters` keeps the existing required `integratedGraphics` boolean and accepts the additional `igpu*` fields as optional CPU parameters. Each stored value keeps its manufacturer source URL and a note for qualifications. CPU graphics capabilities do not guarantee that a motherboard exposes a matching physical connector.

## Catalog CPUs

The catalog's existing CPU set is Intel Core i5-14600KF, Intel Core i5-14600K, and AMD Ryzen 7 7800X3D. The `K` and `KF` Intel models are separate products. The 14600KF is listed by Intel among processors without Intel Graphics; it receives no `igpu*` parameters. Migration V18 appends source-backed details to the existing 14600K and 7800X3D specification rows only. It does not create GPU products or replace existing parameters.

| Existing CPU | Manufacturer data stored as parameters |
| --- | --- |
| [Intel Core i5-14600K](https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html) | `igpuModel=Intel UHD Graphics 770`; `igpuExecutionUnits=32 EU`; `igpuBaseFrequency=300 MHz`; `igpuMaxFrequency=1550 MHz` (Intel's 1.55 GHz Graphics Max Dynamic Frequency); `igpuOutputInterfaces=eDP 1.4b, DisplayPort 1.4a, HDMI 2.1`; HDMI `4096 × 2160 @ 60 Hz`; DisplayPort `7680 × 4320 @ 60 Hz`; eDP `5120 × 3200 @ 120 Hz`; maximum 4 displays. Display resolutions and number of outputs are processor specifications; actual motherboard connectors and routing may limit them. |
| [AMD Ryzen 7 7800X3D](https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html) | `igpuModel=AMD Radeon Graphics`; `igpuCores=2`; `igpuFrequency=2200 MHz` (the source calls this “Graphics Frequency”); `igpuOutputInterfaces=USB Type-C DisplayPort Alternate Mode`. The motherboard must expose a compatible USB-C port. AMD's page does not specify maximum display resolutions or a display count for this processor, so those values are not added. |
| [Intel Core i5-14600KF](https://www.intel.com/content/www/us/en/support/articles/000027144/processors/intel-core-processors.html) | No `igpu*` fields. Intel lists the 14600KF among processors without Intel Graphics. The existing `integratedGraphics=false` remains authoritative. |

## Schema field names

All new fields are optional; omitting a field means the source was not used to verify it for that CPU. `igpuMaxFrequency` is reserved for a source that explicitly identifies a maximum/dynamic graphics frequency. AMD's 2200 MHz value remains `igpuFrequency` because the product specification labels it “Graphics Frequency,” not maximum frequency.

| Code | Type and unit | Meaning |
| --- | --- | --- |
| `igpuModel` | text | Manufacturer's graphics model name |
| `igpuCores` | positive integer, `个` | Graphics core count when the manufacturer supplies it |
| `igpuExecutionUnits` | positive integer, `EU` | Graphics execution-unit count when the manufacturer supplies it |
| `igpuFrequency` | positive number, `MHz` | Graphics frequency when no maximum qualifier is given |
| `igpuBaseFrequency` | positive number, `MHz` | Graphics base frequency |
| `igpuMaxFrequency` | positive number, `MHz` | Explicit graphics maximum/dynamic frequency |
| `igpuOutputInterfaces` | text | Processor-supported display interface modes; board implementation may restrict them |
| `igpuMaxResolutionHdmi` | text | Manufacturer's maximum HDMI resolution and refresh rate |
| `igpuMaxResolutionDisplayPort` | text | Manufacturer's maximum DisplayPort resolution and refresh rate |
| `igpuMaxResolutionEdp` | text | Manufacturer's maximum eDP resolution and refresh rate |
| `igpuMaxDisplays` | positive integer, `台` | Manufacturer's maximum supported display count |

No integrated-graphics rendering benchmark or score is asserted by this document or migration.
