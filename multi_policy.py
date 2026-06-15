"""
    This file plots the label for the multi-application policy.

    The following command can be used to run the program:
     - "python3 multi_policy.py <Training file 1> <Training file 2>
                                <Testing file 3>

    For example, the following command will use the first two case studies as
    training data, and apply it to the third case study.
     - "python3 multi_policy.py
          plotting_data/case1.csv plotting_data/case2.csv
          plotting_data/case3.csv
       "

    The available data files are:
     - plotting_data/case1.csv (The surveillance application)
     - plotting_data/case2.csv (The VR application)
     - plotting_data/case3.csv (The health care system)

    The multi-application works as follows:
     - Dynamic composite score, similar to the COMPOSITE policy.
     - Dynamic evenly distributed labeling ranges, like the MINMAX policy.
"""

import pandas as pd
import matplotlib.pyplot as plt
import math
import sys


ENERGY_NORM_FACTOR = 1000
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
def apply_multi_policy(training, testing):
    df_train_1 = pd.read_csv(training[0])
    df_train_2 = pd.read_csv(training[1])

    # Create the overall datapoints.
    df_train_1["Scenario"] = (
        "Case 1"
        + "-"
        + df_train_1["Configuration"]
        + "-"
        + df_train_1["Workload"]
        + "-"
        + df_train_1["Latency"]
    )

    df_train_2["Scenario"] = (
        "Case 2"
        + "-"
        + df_train_2["Configuration"]
        + "-"
        + df_train_2["Workload"]
        + "-"
        + df_train_2["Latency"]
    )

    # Combine data points into singular list.
    df_train = pd.concat([df_train_1, df_train_2], ignore_index=True)
    df_train["Energy_Usage"] = (df_train["Energy"] / ENERGY_NORM_FACTOR)

    # Calculate the individual data point scores.
    df_train = calculate_scores(df_train, df_train)

    # Calculate ranges for the data points.
    ranges = calculate_ranges(df_train)

    # Apply the ranges to the scores of the training data.
    df_test = pd.read_csv(testing)

    df_test["Scenario"] = (
        df_test["Configuration"]
        + "-"
        + df_test["Workload"]
        + "-"
        + df_test["Latency"]
    )

    df_test["Energy_Usage"] = (df_test["Energy"] / ENERGY_NORM_FACTOR)
    df_test = calculate_scores(df_test, df_train)
    df_test = apply_labels(df_test, ranges)

    # Debug test
    print(df_test[[
        "Scenario",
        "Energy",
        "Energy_Usage",
        "Throughput",
        "Delay",
        "Score",
        "Label"
    ]])

    # Plot the scores from the applied policies.
    plot_policy_scores(df_test, ranges)


# Normalizes the input data to a [0, 1] range.
def normalize(data, data_norm):
    return (data - data_norm.min()) / (data_norm.max() - data_norm.min())


# Calculate the score for all configurations, using the COMPOSITE policy.
def calculate_scores(df, df_norm):
    df = df.copy()

    # Normalize the data using the training scores.
    df["Energy_NORM"] = normalize(df["Energy_Usage"], df_norm["Energy_Usage"])
    df["Throughput_NORM"] = normalize(df["Throughput"], df_norm["Throughput"])
    df["Delay_NORM"] = normalize(df["Delay"], df_norm["Delay"])

    df["Score"] = (df["Throughput_NORM"] - (df["Energy_NORM"] +
                                            df["Delay_NORM"]))

    return df


# Calculates the label classification ranges, using the MINMAX policy.
def calculate_ranges(df):
    scores = df["Score"]
    minimum, maximum = scores.min(), scores.max()
    step = (maximum - minimum) / 7

    labels = ["G", "F", "E", "D", "C", "B", "A"]
    ranges = {}

    for i, label in enumerate(labels):
        low = minimum + i * step
        high = minimum + (i + 1) * step

        ranges[label] = {
            "min": low,
            "max": high
        }

    ranges["G"]["min"] = float("-inf")
    ranges["A"]["max"] = float("inf")

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
def plot_policy_scores(df, ranges):
    df = df.sort_values(
        by="Score",
        ascending=False
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
        "Multi-application Policy Classification"
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
        minimum = r.get("min", None)
        maximum = r.get("max", None)

        if (isinstance(minimum, (int, float))
                and math.isinf(minimum) and minimum < 0):
            if isinstance(maximum, (int, float)):
                maximum = f"{maximum:.2f}"
            range_text = f"≤ {maximum}"

        elif (isinstance(maximum, (int, float))
                and math.isinf(maximum) and maximum > 0):
            if isinstance(minimum, (int, float)):
                minimum = f"{minimum:.2f}"
            range_text = f"≥ {minimum}"

        else:
            if isinstance(minimum, (int, float)):
                minimum = f"{minimum:.2f}"
            if isinstance(maximum, (int, float)):
                maximum = f"{maximum:.2f}"

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
    if len(sys.argv) != 4:
        print(
            "Usage: python main.py <csv_file 1> <csv_file 2> <csv_file 3>"
        )
        sys.exit(1)

    # Read the input arguments.
    training_data = [sys.argv[1], sys.argv[2]]
    testing_data = sys.argv[3]

    # Run the program.
    apply_multi_policy(
        training_data,
        testing_data
    )
