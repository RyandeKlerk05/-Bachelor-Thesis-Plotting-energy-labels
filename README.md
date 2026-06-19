# -Bachelor-Thesis-Plotting-energy-labels
Repository containing the code, data, and resulting figures used in the bachelor thesis "Assigning Energy Labels for Digital Services".

**The repository includes:**
 - iFogSim2 simulation files for the case studies
 - Raw simulation output data
 - Python scripts for applying the proposed labeling policies
 - Generated figures used throughout the thesis

**Files:**

 Simulation data:
  - Case1.java: The iFogSim simulation file for the surveillance application.
  - Case2.java: The iFogSim simulation file for the VR application.
  - Case3.java: The iFogSim simulation file for the healthcare system.

Applying policies:
 - plot_data.py: Calculates scores and ranges, applies the policies and plots the data.
 - multi_policy.py: Applies the multi-application policy on multiple datasets at once.

/plotting_data:
 - case1.csv: Raw simulation data for the surveillance application.
 - case2.csv: Raw simulation data for the VR application.
 - case3.csv: Raw simulation data for the healtcare system.

/results:
 - /case1_figures: The bar graphs for each policy applied to the surveillance application.
 - /case2_figures: The bar graphs for each policy applied to the VR application.
 - /case3_figures: The bar graphs for the multi-application policy.
