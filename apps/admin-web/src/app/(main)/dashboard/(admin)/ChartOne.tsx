"use client";

import React from "react";
import { Chart } from "primereact/chart";
import { get7days } from "@/utils";

export default function ChartOne() {
  const chartOptions = {
    plugins: {
      legend: {
        display: false,
      },
    },
    scales: {
      x: {
        ticks: {
          color: "#000",
          font: {
            size: 10,
            weight: "normal",
          },
        },
        grid: {
          color: "transparent",
        },
      },
      y: {
        beginAtZero: true,
        ticks: {
          color: "#000",
          font: {
            size: 10,
            weight: "normal",
          },
        },
        grid: {
          color: "#EFEFEF",
        },
      },
    },
  };

  const chartData = {
    labels: get7days(),
    datasets: [
      {
        data: [25, 15, 18, 38, 20, 37, 24],
        backgroundColor: "#883cae",
        borderColor: "#883cae",
        borderWidth: 1,
      },
    ],
  };

  return (
    <>
      <Chart type="bar" data={chartData} options={chartOptions} />
      <span className="chart-panel-info purple">Today&apos;s Order</span>
    </>
  );
}
