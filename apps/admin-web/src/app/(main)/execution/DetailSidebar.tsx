import { useEffect, useState } from "react";
import { Button } from "primereact/button";
import { Sidebar } from "primereact/sidebar";
import { get, map } from "lodash-es";
import InfoItems from "@/components/Info/InfoItems";
import InfoList from "@/components/Info/InfoList";
import { getExecutionDetail } from "@/api/execution";
import {
  BASIC_INFO_FIELD_MAP,
  INSTRUMENT_FIELD_MAP,
  MISC_FIELD_MAP,
  SEC_ALT_FIELD_MAP,
} from "./fieldMap";
import { useRouter } from "next/navigation";

interface IExecDetailSidebarProps {
  id: string; // exec.internalId
  data: any; // exec whole data
  visible: boolean;
  onHide: () => void;
}

export default function ExecDetailSidebar(props: IExecDetailSidebarProps) {
  const router = useRouter();
  const [detail, setDetail] = useState<{ [T: string]: any }>({});
  const [showJump, setShowJump] = useState<boolean>(false);

  const getDetailInfo = async () => {
    console.log(props.id)
    getExecutionDetail(props.id).then((res: any) => {
      if (res?.serviceStatus?.success) {
        const data = res?.data ?? {};

        // basic info
        const basicInfo: any[] = [];
        for (let [label, field] of Object.entries(BASIC_INFO_FIELD_MAP)) {
          switch (label) {
            case "Execution Status":
              const val = data[field];
              const tm: any = {
                Allocated: "Allocation Detail",
                Invalid: "Exception Monitor",
              };
              setShowJump(val === "Valid");
              basicInfo.push({
                label: label,
                value: (
                  <>
                    <span style={{ color: "#883cae" }}>{val}</span>
                    {tm[val] ? (
                      <span
                        className="flex items-center"
                        style={{
                          color: "#fff",
                          backgroundColor: "#883cae",
                          padding: "3px",
                          borderRadius: "2px",
                          width: "fit-content",
                        }}
                      >
                        {tm[val]}
                      </span>
                    ) : null}
                  </>
                ),
              });
              break;
            default:
              basicInfo.push({
                label: label,
                value: data[field] ?? "",
              });
          }
        }

        // instrument info
        const instrumentInfo: unknown[] = [];
        let instruSecAlt = {};
        for (let [label, field] of Object.entries(INSTRUMENT_FIELD_MAP)) {
          switch (label) {
            case "Security Alts":
              const arr = data.instrument?.[field]; // array of security alts (data)
              const fields: any = {}; // key: value[]
              for (let [_l, _f] of Object.entries(SEC_ALT_FIELD_MAP)) {
                fields[_l] = map(arr, (slItem: any) => slItem[_f] ?? "");
              }

              instruSecAlt = {
                label: label,
                value: fields,
              };
              break;
            default:
              instrumentInfo.push({
                label: label,
                value: data.instrument?.[field] ?? "",
              });
          }
        }

        // misc fees
        const miscFeesData = data.miscFees ?? [];
        let miscFees: any = {};
        for (let [_l, _f] of Object.entries(MISC_FIELD_MAP)) {
          miscFees[_l] = map(miscFeesData, (slItem: any) => slItem[_f] ?? "");
        }

        const detail: any = {};
        detail["basicInfo"] = basicInfo;
        detail["Instrument"] = instrumentInfo;
        detail["instruSecAlt"] = instruSecAlt;
        detail["miscFees"] = miscFees;

        console.info("detail", detail);
        setDetail(detail);
      }
    });
  };

  useEffect(() => {
    if (props.visible && props.id) {
      getDetailInfo();
    } else {
      setDetail({});
    }
  }, [props.id, props.visible]);

  return (
    <>
      <Sidebar
        visible={props.visible}
        position="right"
        onHide={props.onHide}
        header={() => (
          <p className="main-title">
            <span>Execution Detail</span>
            <span className="main-title-desc">{props.id}</span>
          </p>
        )}
        showCloseIcon={false}
      >
        {/* basic info card */}
        <div className="main-card-dark mb-4">
          <p className="main-subtitle mb-3.5">Basic Info</p>
          <div className="info-card grid grid-cols-2 gap-y-4 gap-x-5">
            <InfoItems data={detail["basicInfo"]} />
          </div>
        </div>

        {/* Instrument info card */}
        <div className="main-card-dark mb-4">
          <p className="main-subtitle mb-3.5">Instrument</p>
          <div className="info-card">
            <div className="grid grid-cols-2 gap-y-4 gap-x-5 mb-4">
              <InfoItems data={detail["Instrument"]} />
            </div>
            <InfoList
              label={get(detail, "instruSecAlt.label", "")}
              value={get(detail, "instruSecAlt.value", {})}
              styleClass="grid-cols-2"
            />
          </div>
        </div>

        {/* Misc Fees info card */}
        <div className="main-card-dark mb-4">
          <p className="main-subtitle mb-3.5">Misc Fees</p>
          <div className="info-card">
            <InfoList
              value={get(detail, "miscFees", {})}
              styleClass="grid-cols-4"
            />
          </div>
        </div>

        {showJump ? (
          <div className="exec-detail-footer operate-footer py-3.5">
            <Button
              rounded
              label="Execution Correction"
              severity="help"
              onClick={() => {
                router.push(`/execution/correction/${props.data?.execId}`);
              }}
            />
            <Button
              rounded
              label="Execution Cancellation"
              severity="help"
              onClick={() => {
                router.push(`/execution/cancellation/${props.data?.execId}`);
              }}
            />
          </div>
        ) : null}
      </Sidebar>
    </>
  );
}
