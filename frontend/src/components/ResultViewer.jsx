import React from "react";
// Classic JSON viewer: using plain <pre> for raw output (no external viewer)

const ResultViewer = ({ result }) => {
  // Helper: desired display fields (label and canonical key)
  const displayFields = [
    { label: "Name", key: "name" },
    { label: "Phone", key: "phone" },
    { label: "Email", key: "email" },
    { label: "Address", key: "address" },
    { label: "Postcode", key: "postcode" },
    { label: "Date of Birth", key: "dob" },
  ];

  // If backend returned pages/fields schema, flatten into a map for easy lookup
  const fieldMap = {};
  if (result && result.pages && Array.isArray(result.pages) && result.pages.length > 0) {
    const farr = result.pages[0].fields || [];
    farr.forEach((f) => {
      if (f && f.name) fieldMap[f.name] = f;
    });
  }

  // Helper to show a field value and confidence
  const renderValue = (key) => {
    const entry = fieldMap[key];
    if (!entry) return <span className="text-gray-400">—</span>;
    return (
      <div>
        <div className="whitespace-pre-line max-w-lg break-words">{entry.value || ""}</div>
        <div className="text-xs text-gray-400 mt-1">confidence: {typeof entry.confidence === 'number' ? entry.confidence : 0}</div>
      </div>
    );
  };

  return (
    <div className="mt-6 bg-white p-6 rounded-xl shadow-md">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-800">Extracted Data</h2>
      </div>

      {/* Table view for structured fields
      <div className="overflow-x-auto mt-4">
        <table className="min-w-full border text-sm">
          <tbody>
            {displayFields.map(({ label, key }) => (
              <tr key={key} className="border-b last:border-b-0">
                <td className="font-medium px-2 py-1 bg-gray-50 w-32">{label}</td>
                <td className="px-2 py-1">{renderValue(key)}</td>
              </tr>
            ))}
            <tr>
              <td className="font-medium px-2 py-1 bg-gray-50">OCR Text</td>
              <td className="px-2 py-1">{(fieldMap['ocrText'] && fieldMap['ocrText'].value) || <span className="text-gray-400">—</span>}</td>
            </tr>
          </tbody>
        </table>
      </div> */}

      {/* Classic JSON view (plain text) */}
      <div className="mt-6">
        <div className="mb-2 font-semibold text-gray-600">Raw JSON</div>
        <pre style={{ whiteSpace: 'pre-wrap' }}>{result ? JSON.stringify(result, null, 2) : ''}</pre>
      </div>
    </div>
  );
};

export default ResultViewer;
