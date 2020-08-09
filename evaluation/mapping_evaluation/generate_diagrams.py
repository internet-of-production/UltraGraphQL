import pandas as pd
import numpy as np
import matplotlib.pyplot as plt


if __name__ == '__main__':
    df_literal = pd.read_csv("results_only_literal.csv", delimiter=';')
    df_types = pd.read_csv("results_type.csv", delimiter=';')
    df_eq_class = pd.read_csv("results_equivalent_class.csv", delimiter=';')
    df_eq_property = pd.read_csv("results_equivalent_property.csv", delimiter=';')

    fig = plt.figure()

    print(df_types)
    plt.plot(df_literal['amount'], df_literal['time'], label='String')
    plt.plot(df_types['amount'], df_types['time'], label='Object Type')
    plt.xlabel("Number of Classes");
    plt.ylabel("Execution Time (in s)");
    plt.title("Schema Mapping Time");
    plt.legend();
    plt.show()

    fig2 = plt.figure()
    plt.plot(df_eq_class['amount'], df_eq_class['time'], label='Equivalent Properties')
    plt.plot(df_eq_property['amount'], df_eq_property['time'], label='Equivalent Classes')
    plt.xlabel("Number of Equivalences");
    plt.ylabel("Execution Time (in s)");
    plt.title("Schema Mapping Time");
    plt.legend();
    plt.show()