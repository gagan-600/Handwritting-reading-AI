import React, { useRef, useState } from "react";
import axios from "axios";

const UploadForm = ({ setResult }) => {
  const inputRef = useRef(null);
  const [fileName, setFileName] = useState("");
  const [loading, setLoading] = useState(false);

  const onFileSelected = async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    setFileName(file.name);
    setLoading(true);
    const formData = new FormData();
    formData.append("file", file);

    try {
      const res = await axios.post("http://localhost:5000/api/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      setResult(typeof res.data === "string" ? JSON.parse(res.data) : res.data);
    } catch (err) {
      console.error(err);
      alert("Upload failed: " + (err.message || err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white p-6 rounded-xl shadow-md flex flex-col items-center gap-4">
      <input
        ref={inputRef}
        type="file"
        accept="image/*,application/pdf"
        onChange={onFileSelected}
        className="hidden"
      />

      <button
        onClick={() => inputRef.current && inputRef.current.click()}
        className="w-full max-w-xs bg-gradient-to-r from-blue-600 to-indigo-600 text-white px-6 py-3 rounded-lg font-medium shadow hover:from-blue-700 hover:to-indigo-700"
      >
        {loading ? "Processing…" : "Select & Upload"}
      </button>

      <div className="text-sm text-gray-500">{fileName || "No file selected"}</div>
    </div>
  );
};

export default UploadForm;
