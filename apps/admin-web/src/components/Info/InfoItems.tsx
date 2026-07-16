"use client";

import React from "react";

export interface IDataItem {
  lable: string;
  value: string | JSX.Element;
}

export interface IInfoItemsProps {
  data?: IDataItem[];
}

const InfoItems = ({ data }: IInfoItemsProps) => {
  const getItems = (arr: any[]) => {
    if (Array.isArray(arr) && arr.length > 0) {
      const els = arr.map((item: any, index: number) => {
        return (
          <div key={index} className="info-item flex flex-col gap-1">
            <div className="info-item-title">{item.label}</div>
            <div className="info-item-value">{item.value ? item.value : '-'}</div>
          </div>
        );
      });
      return <>{els}</>;
    } else {
      return null;
    }
  };

  return <>{data ? getItems(data) : null}</>;
};

export default InfoItems;
