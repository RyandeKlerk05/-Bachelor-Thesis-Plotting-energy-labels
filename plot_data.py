"""
    This file reads the data simulated by iFogSim,
    and applies the proposed labeling policies on it,
    by calculating the scores and labeling ranges.

    The following command can be used to run the program:
     - "python3 plot_data.py <DATA file> <POLICY>"

    For example, the following command will apply the MEDIAN policy
    on the data of case study 1 (the surveillance system):
     - "python3 plot_data.py plotting_data/case1.csv MINMAX"

    The available data files are:
     - plotting_data/case1.csv (The surveillance application)
     - plotting_data/case2.csv (The VR application)

    The available policies are:
     - MINMAX
     - MEDIAN
     - PERCENTILE
     - RATIO
     - COMPOSITE
     - COMPOSITE_DYNAMIC
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from enum import Enum
import sys


class PolicyType(Enum):
    MINMAX = 1
    MEDIAN = 2
    PERCENTILE = 3
    RATIO = 4
    COMPOSITE = 5
    COMPOSITE_DYNAMIC = 6


ENERGY_NORMALIZATIOn_FACTOR = 1000
LABEL_COLORS = {
    "A": "green",
    "B": "limegreen",
    "C": "yellowgreen",
    "D": "yellow",
    "E": "orange",
    "F": "orangered",
    "G": "red"
}


# Normalizes the input data to a [0, 1] range.
def normalize(data):
    return (data - data.min()) / (data.max() - data.min())


# Applies the given policy to the test data.
def apply_policy(filename, policy):
    df = pd.read_csv(filename)

    # Create the overall datapoints.
    df["Scenario"] = (
        df["Configuration"]
        + "-"
        + df["Workload"]
        + "-"
        + df["Latency"]
    )

    df["Energy_Usage"] = (df["Energy"] / ENERGY_NORMALIZATIOn_FACTOR)          

    df = calculate_scores(df, policy)
    ranges = calculate_ranges(df, policy)
    df = apply_labels(df, ranges)

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
    plot_policy_scores(df, ranges, policy)


# Calculate the score for all configurations, depending on the policy.
def calculate_scores(df, policy):
    df = df.copy()

    # Single metric policies:
    if policy in [
        PolicyType.MINMAX,
        PolicyType.MEDIAN,
        PolicyType.PERCENTILE
    ]:
        df["Score"] = df["Energy_Usage"]

    # Ratio policy:
    elif policy == PolicyType.RATIO:
        df["Score"] = df["Throughput"] / df["Energy_Usage"]

    # Composite policy:
    elif policy == PolicyType.COMPOSITE:
        # Normalize the data
        df["Energy_NORM"] = normalize(df["Energy_Usage"])
        df["Throughput_NORM"] = normalize(df["Throughput"])
        df["Delay_NORM"] = normalize(df["Delay"])

        df["Score"] = (df["Throughput_NORM"] -
                       (df["Energy_NORM"] + df["Delay_NORM"]))

    # Dynamic composite policy:
    elif policy == PolicyType.COMPOSITE_DYNAMIC:
        # Normalize the data
        E = normalize(df["Energy_Usage"])
        T = normalize(df["Throughput"])
        D = normalize(df["Delay"])

        scores = []

        # Increase the effect of the relative largest variable.
        for i, (t, e, d) in enumerate(zip(T, E, D)):
            candidates = {
                "throughput": t,
                "energy": 1 - e,   # inverted because lower energy is better.
                "delay": 1 - d     # inverted because lower delay is better.
            }

            dominant = max(candidates, key=candidates.get)

            # Apply the weighting based on dominant parameter.
            if dominant == "throughput":
                score = 2 * t - (e + d)
            elif dominant == "energy":
                score = t - (2 * e + d)
            else:  # delay-sensitive
                score = t - (e + 2 * d)

            # Debug to show dominant parameter.
            if dominant == "throughput":
                print(f'{df.iloc[i]["Scenario"]}: throughput')
            elif dominant == "energy":
                print(f'{df.iloc[i]["Scenario"]}: energy')
            else:
                print(f'{df.iloc[i]["Scenario"]}: delay')

            scores.append(score)
        df["Score"] = scores

    return df


# Calculates the label classification ranges.
def calculate_ranges(df, policy):
    scores = sorted(df["Score"])
    labels = ["A", "B", "C", "D", "E", "F", "G"]
    ranges = {}

    # Min-Max Policy:
    if policy == PolicyType.MINMAX:
        minimum, maximum = min(scores), max(scores)
        step = (maximum - minimum) / 7

        for i, label in enumerate(labels):
            low = minimum + i * step
            high = minimum + (i + 1) * step

            ranges[label] = {
                "min": low,
                "max": high,
            }

    # Median-based policies:
    elif policy == PolicyType.MEDIAN:
        median = np.median(scores)

        ranges = {
            "A": {"max": median * 0.5},
            "B": {"min": median * 0.5, "max": median * 0.7},
            "C": {"min": median * 0.7, "max": median * 0.9},
            "D": {"min": median * 0.9, "max": median * 1.1},
            "E": {"min": median * 1.1, "max": median * 1.3},
            "F": {"min": median * 1.3, "max": median * 1.5},
            "G": {"min": median * 1.5}
        }

    # Percentile-based policies:
    else:

        labels = ["A", "B", "C", "D", "E", "F", "G"]

        # Reverses the percentile order for policies that want a high score.
        if policy == PolicyType.PERCENTILE:
            percentiles = [5, 15, 25, 40, 55, 70, 100]
            previous = min(scores)

            for label, p in zip(labels, percentiles):
                boundary = np.percentile(scores, p)

                ranges[label] = {
                    "min": previous,
                    "max": boundary
                }

                previous = boundary

        else:
            percentiles = [95, 85, 75, 60, 45, 30, 0]
            previous = max(scores)

            for label, p in zip(labels, percentiles):
                boundary = np.percentile(scores, p)

                ranges[label] = {
                    "min": min(boundary, previous),
                    "max": max(boundary, previous)
                }

                previous = boundary

    return ranges


# Applies the labels to the calculated scores.
def apply_labels(df, ranges):
    labels = []
    colors = []

    for score in df["Score"]:
        assigned_label = "G"

        # Go through the labels until the item is withing the bounds.
        for label, r in ranges.items():

            minimum = r.get("min", float("-inf"))
            maximum = r.get("max", float("inf"))

            if minimum <= score <= maximum:

                assigned_label = label
                break

        labels.append(assigned_label)
        colors.append(LABEL_COLORS[assigned_label])

    df["Label"] = labels
    df["Color"] = colors

    return df


# Plots the data with the applies labels from the policy.
def plot_policy_scores(df, ranges, policy):

    # Order the data from best to worst (depending on type of score).
    ascending = policy in [
        PolicyType.MINMAX,
        PolicyType.MEDIAN,
        PolicyType.PERCENTILE
    ]

    df = df.sort_values(
        by="Score",
        ascending=ascending
    )

    # Plot the figure.
    fig, (ax_graph, ax_table) = plt.subplots(
        1, 2,
        figsize=(16, 7),
        gridspec_kw={"width_ratios": [4, 1]}
    )

    # Create the bar graph with the data.
    bars = ax_graph.bar(
        df["Scenario"],
        df["Score"],
        color=df["Color"]
    )

    # Add labels above bars
    for bar, label in zip(bars, df["Label"]):

        height = bar.get_height()

        ax_graph.text(
            bar.get_x() + bar.get_width() / 2,
            height,
            label,
            ha="center",
            va="bottom",
            fontsize=9,
            fontweight="bold"
        )

    ax_graph.set_title(
        f"{policy.name} Policy Classification"
    )

    ax_graph.set_xlabel("Configuration")
    ax_graph.set_ylabel("Score")
    ax_graph.set_axisbelow(True)

    ax_graph.grid(
        axis="y",
        linestyle="--",
        alpha=0.5
    )

    ax_graph.axhline(
        y=0,
        color="black",
        linewidth=1,
        alpha=0.7
    )

    ax_graph.tick_params(
        axis="x",
        rotation=90
    )

    # Plot table with exact range values.
    ax_table.axis("off")

    table_data = []

    labels = ["A", "B", "C", "D", "E", "F", "G"]

    for label in labels:

        r = ranges[label]

        minimum = r.get("min", "-")
        maximum = r.get("max", "-")

        if isinstance(minimum, (int, float)):
            minimum = f"{minimum:.2f}"

        if isinstance(maximum, (int, float)):
            maximum = f"{maximum:.2f}"

        if minimum == "-":
            range_text = f"≤ {maximum}"

        elif maximum == "-":
            range_text = f"≥ {minimum}"

        else:
            range_text = f"{minimum} - {maximum}"

        table_data.append([
            label,
            range_text
        ])

    table = ax_table.table(
        cellText=table_data,
        colLabels=["Label", "Range"],
        cellLoc="center",
        loc="center"
    )

    table.scale(1.2, 2)

    # Color label cells
    for i, label in enumerate(labels):

        table[(i + 1, 0)].set_facecolor(
            LABEL_COLORS[label]
        )

    ax_table.set_title("Ranges")

    # Plot the final figure.
    plt.tight_layout()
    plt.show()


if __name__ == "__main__":

    # Check if all necessary inputs are given.
    if len(sys.argv) != 3:
        print(
            "Usage: python main.py <csv_file> <policy>"
        )
        sys.exit(1)

    # Read the input arguments.
    filename = sys.argv[1]
    policy = PolicyType[sys.argv[2].upper()]

    # Run the program.
    apply_policy(
        filename,
        policy
    )
