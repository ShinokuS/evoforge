# V14 parity milestones

The intermediate exact historical-oracle gates were proven before the final V14 gate replaced them:

- pre-bathymetry parity: `ced1789f00c42002b5fee89d7327c2d6710cf8fa`
- coastal bathymetry parity: `a3c24f018a0d211812dc565032aa331c5589a1c2`

`V14BathymetryHistoricalOracleParityTest` is the permanent V14 acceptance gate. It compares the complete historical dense V14 result against Continuum cell-for-cell and requires the deep-interior pass to modify at least one fixture cell.

The intermediate tests are intentionally not retained in the regular CI suite because each independently re-executes the same exact V12 -> V13 -> V14 coastal global pipeline and caused the complete suite to exceed its job time limit. Their source remains available in the commits above.
