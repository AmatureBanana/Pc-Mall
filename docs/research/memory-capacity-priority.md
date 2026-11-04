# Seeded memory specification check

Checked 2026-11-04 against the product seed migrations and Kingston first-party sources. Scope: seeded `MEMORY` SKUs, capacity, DDR generation, and the matching vendor-rated rate/timing profile. This records published product settings, not a verified system operating result.

## Seed inventory

The migrations contain one `MEMORY` product SKU: `KINGSTON-KF560C36BBEK2-32`, model `KF560C36BBEK2-32`, in [V2 demo products](../../backend/src/main/resources/db/migration/V2__demo_products.sql). [V4 specifications](../../backend/src/main/resources/db/migration/V4__catalog_specifications.sql) adds its `catalog-parameters-v1` record (`verified_on=2026-10-30`): DDR5, 32 GB kit capacity, 2 × 16 GB, 6000 MT/s EXPO/XMP and 4800 MT/s JEDEC. V4 does not store CAS/tRCD/tRP fields. [V15](../../backend/src/main/resources/db/migration/V15__trusted_performance_demo.sql) adds an Intel CPU and no memory product.

## Exact seeded SKU

| SKU | Capacity and generation | Matching rated profile | JEDEC default | Source identity and current status |
| --- | --- | --- | --- | --- |
| `KF560C36BBEK2-32` | 32 GB kit (2 × 16 GB), DDR5; non-ECC unbuffered DIMM kit | EXPO Profile #0 and XMP Profile #1: DDR5-6000, 36-38-38 at 1.35 V — CL36 / tRCD 38 / tRP 38 | DDR5-4800, 40-39-39 at 1.1 V | Kingston [exact-SKU datasheet](https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf), Document No. 4810622A, printed date `08/10/22`; Kingston's [exact-SKU parts page](https://www.kingston.com/en/memory/search/discontinuedmodels?partid=kf560c36bbek2-32), checked 2026-11-04, marks the SKU discontinued and says it is replaced by `KF560C36BBE2K2-32`. |

The datasheet identifies the capacity, generation, tested 6000 MT/s timing, and the EXPO/XMP profile settings. It explicitly cautions that some motherboards or system configurations may not operate at Kingston's published speeds and timings. Treat 6000 MT/s and 36-38-38 as the product's rated profile, not a guaranteed runtime setting.

## Keep the successor SKU separate

The replacement is a different model number: Kingston lists `KF560C36BBE2K2-32` with a 6000 MT/s `36-44-44` profile ([Kingston replacement product page](https://www.kingston.com/en/memory/search?partid=KF560C36BBE2K2-32), checked 2026-11-04). Do not use that timing tuple for the seeded `KF560C36BBEK2-32`, whose archived exact-SKU datasheet and current discontinued-model listing both specify `36-38-38`.

## Sources

- [V2 demo product seed](../../backend/src/main/resources/db/migration/V2__demo_products.sql)
- [V4 catalog specifications seed](../../backend/src/main/resources/db/migration/V4__catalog_specifications.sql)
- [V15 trusted-performance demo seed](../../backend/src/main/resources/db/migration/V15__trusted_performance_demo.sql)
- [Kingston KF560C36BBEK2-32 datasheet](https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf)
- [Kingston KF560C36BBEK2-32 discontinued-model listing](https://www.kingston.com/en/memory/search/discontinuedmodels?partid=kf560c36bbek2-32)
- [Kingston KF560C36BBE2K2-32 replacement listing](https://www.kingston.com/en/memory/search?partid=KF560C36BBE2K2-32)
