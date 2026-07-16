"use client";

import { useState } from "react";
import { Chart } from "primereact/chart";
import { get6months } from "@/utils";

export default function ChartThree() {
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
    labels: get6months(),
    datasets: [
      {
        data: [20, 43, 25, 16, 25, 10, 40],
        fill: true,
        borderColor: "#63ABFD",
        backgroundColor: "rgba(99,171,253,0.6)",
        tension: 0.4,
        pointRadius: 0,
      },
      {
        data: [8, 20, 40, 20, 16, 30, 30],
        fill: true,
        borderColor: "#883CAE",
        backgroundColor: "rgb(230,151,255,0.6)",
        tension: 0.4,
        pointRadius: 0,
      },
      {
        data: [12, 51, 62, 33, 21, 62, 45],
        fill: true,
        borderColor: "#F765A3",
        backgroundColor: "rgb(255,165,203,0.6)",
        tension: 0.4,
        pointRadius: 0,
      },
    ],
  });

  return (
    <>
      <Chart type="line" data={chartData} options={chartOptions} />
      <span className="chart-panel-info blue">Investor placing an order</span>
      <span className="chart-panel-info pink1">Investor registered</span>
      <span className="chart-panel-info pink2">Investor logged</span>
    </>
  );
}
