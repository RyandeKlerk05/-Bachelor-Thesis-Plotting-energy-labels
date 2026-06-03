"""
    This file plots the label for the multi-application policy.

    The following command can be used to run the program:
     - "python3 multi_policy.py <DATA file 1> <DATA file 2>

    For example, the following command will apply the policy on the
    data of case study 1 (the surveillance system) and 2 (the VR game):
     - "python3 multi_policy.py plotting_data/case1.csv
                                plotting_data/case2.csv"

    The available data files are:
     - plotting_data/case1.csv (The surveillance application)
     - plotting_data/case2.csv (The VR application)


    The multi-application works as follows:
     - Dynamic composite score, similar to the COMPOSITE DYNAMIC policy.
     - Dynamic evenly distributed labeling ranges, like the MINMAX policy.
"""

import plot_data
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from enum import Enum
import sys


ENERGY_NORMALIZATION_FACTOR = 1000
LABEL_COLORS = {
    "A": "green",
    "B": "limegreen",
    "C": "yellowgreen",
    "D": "yellow",
    "E": "orange",
    "F": "orangered",
    "G": "red"
}


# Applies the given policy to the test data.
def apply_multi_policy(filename1, filename2):
    df_1 = pd.read_csv(filename1)
    df_2 = pd.read_csv(filename2)

    # Create the overall datapoints.
    df_1["Scenario"] = (
        "Case 1"
        + "-"
        + df_1["Configuration"]
        + "-"
        + df_1["Workload"]
        + "-"
        + df_1["Latency"]
    )

    df_2["Scenario"] = (
        "Case 2"
        + "-"
        + df_2["Configuration"]
        + "-"
        + df_2["Workload"]
        + "-"
        + df_2["Latency"]
    )

    df_1["Energy_Usage"] = (df_1["Energy"] / ENERGY_NORMALIZATION_FACTOR)
    df_2["Energy_Usage"] = (df_2["Energy"] / ENERGY_NORMALIZATION_FACTOR)

    # Calculate the individual data point scores.
    df_1 = plot_data.calculate_scores(df_1,
                                      plot_data.PolicyType.COMPOSITE)
    df_2 = plot_data.calculate_scores(df_2,
                                      plot_data.PolicyType.COMPOSITE)

    # Combine data points into singular list.
    df = pd.concat([df_1, df_2], ignore_index=True)

    # Calculate ranges for the data points.
    ranges = plot_data.calculate_ranges(df, plot_data.PolicyType.MINMAX)

    # Apply labels to the data.
    df = plot_data.apply_labels(df, ranges)

    # Debug test
    print(df[[
        "Scenario",
        "Energy",
        "Energy_Usage",
        "Throughput",
        "Delay",
        "Score",
        "Label"
    ]])

    # Plot the scores from the applied policies.
    plot_data.plot_policy_scores(df, ranges, plot_data.PolicyType.COMPOSITE)


if __name__ == "__main__":

    # Check if all necessary inputs are given.
    if len(sys.argv) != 3:
        print(
            "Usage: python main.py <csv_file 1> <csv_file 2>"
        )
        sys.exit(1)

    # Read the input arguments.
    filename1 = sys.argv[1]
    filename2 = sys.argv[2]

    # Run the program.
    apply_multi_policy(
        filename1,
        filename2
    )
