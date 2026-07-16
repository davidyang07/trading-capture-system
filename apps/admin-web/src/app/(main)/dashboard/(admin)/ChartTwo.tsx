"use client";

import { useState } from "react";
import { Chart } from "primereact/chart";
import { get7days } from "@/utils";

export default function ChartTwo() {
  const chartOptions = {
    maintainAspectRatio: false,
    aspectRatio: 1,
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

  const [chartData] = useState({
    labels: get7days(),
    datasets: [
      {
        data: [20, 43, 25, 16, 25, 10, 40],
        borderColor: "#883CAE",
        tension: 0.4,
        pointRadius: 0,
      },
      {
        data: [8, 20, 40, 20, 16, 30, 30],
        borderColor: "#63ABFD",
        tension: 0.4,
        pointRadius: 0,
      },
    ],
  });

  return (
    <>
      <Chart type="line" data={chartData} options={chartOptions} />
      <span className="chart-panel-info purple">Executions for today</span>
      <span className="chart-panel-info blue">Allocation for today</span>
    </>
  );
}
