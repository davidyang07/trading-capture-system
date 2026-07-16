export function FormLabel({ required, label }: any) {
  return (
    <div className="form-label flex items-center">
      {/* required icon */}
      {required && <i className="pi pi-asterisk" />}
      {label}
    </div>
  );
}

export function valid(meta: any) {
  return !(meta.touched && meta.error);
}

export function errMsg(meta: any) {
  if (meta.error && meta.touched) {
    return <small className="err">{meta.error}</small>;
  }
}

export function itemClass(meta: any) {
  if (meta.error && meta.touched) {
    return "form-item invalid";
  } else {
    return "form-item";
  }
}
