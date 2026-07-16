"use client";

import { map } from "lodash-es";
import React from "react";

export interface IInfoListProps {
  label?: string;
  styleClass?: string;
  value: {
    [T: string]: string[];
  };
}

// detail list info
const InfoList = (props: IInfoListProps) => {
  const getItems = () => {
    const els = Object.entries(props.value).map(([fieldName, vals]: any) => {
      return (
        <div key={fieldName} className="info-item flex flex-col gap-1">
          <div
            className={`${
              props.label ? "info-item-field-title" : "info-item-title"
            }`}
          >
            {fieldName}
          </div>
          {map(vals, (_v, _i) => {
            return (
              <div className="info-item-value" key={_i}>
                {_v ? _v : "-"}
              </div>
            );
          })}
        </div>
      );
    });

    return els;
  };

  return (
    <>
      {props.label && <div className="info-item-title">{props.label}</div>}
      <div className={`grid ${props.styleClass} gap-y-4 gap-x-5`}>
        {getItems()}
      </div>
    </>
  );
};

export default InfoList;
