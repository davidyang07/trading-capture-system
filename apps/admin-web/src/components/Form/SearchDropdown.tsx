import React, { useEffect, useState } from "react";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";

export interface ISearchDropdown {
  value: any;
  label?: string;
  required?: boolean;
  searchFn: (search: string) => Promise<any>;
  onChange: (e: any) => void;
  [T: string]: any;
}

const SearchDropdown: React.FC<ISearchDropdown> = ({
  searchFn,
  ...props
}: ISearchDropdown) => {
  const [search, setSearch] = useState<any>("");
  const [options, setOptions] = useState<any>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [valid, setValid] = useState<boolean>(true);

  // form validator
  const validate = () => {
    if (props.required && !props.value) {
      setValid(false);
    } else {
      setValid(true);
    }
  };

  useEffect(() => {
    validate();
  }, [props.value, props.required]);

  useEffect(() => {
    if (searchFn) {
      const request = searchFn;
      setLoading(true);
      request(search)
        .then((res: any) => {
          setOptions([...res]);
          setLoading(false);
        })
        .finally(() => {
          setLoading(false);
        })
        .catch(() => {
          setLoading(false);
        });
    }
  }, [search]);

  useEffect(() => {
    return () => {
      // clear search state
      setSearch("");
      setOptions([]);
      setLoading(false);
      setValid(true);
    };
  }, []);

  return (
    <div
      className={`form-item flex flex-col gap-3.5 ${!valid ? "invalid" : ""}`}
    >
      {props.label && (
        <label className="form-label flex items-center">
          {props.required && <i className="pi pi-asterisk" />}
          {props.label}
        </label>
      )}
      <Dropdown
        {...props}
        filter
        value={props.value}
        options={options}
        loading={loading}
        optionLabel={props.optionLabel}
        optionValue={props.optionValue}
        placeholder={props.placeholder}
        onChange={(e: any) => props.onChange(e)}
        filterTemplate={() => (
          <InputText
            value={search}
            onChange={(e) => setSearch(e.target?.value ?? "")}
            style={{ width: "100%" }}
          />
        )}
      />
      {!valid ? (
        <small style={{ marginTop: "-10px", color: "#FF0000" }}>
          {"required"}
        </small>
      ) : null}
    </div>
  );
};

export default SearchDropdown;
