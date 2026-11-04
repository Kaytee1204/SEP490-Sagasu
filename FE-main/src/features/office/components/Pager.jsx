import Pagination from "@/components/common/Pagination";

export default function Pager({ data, onChange }) {
  return (
    data &&
    (data.totalElements > 0 || data.page > 1) && (
      <footer className="of-pager">
        <span className="of-muted">
          {data.totalElements} kết quả
          {data.totalPages > 1 && ` · Trang ${data.page}/${data.totalPages}`}
        </span>
        <Pagination
          currentPage={data.page}
          totalPages={data.totalPages}
          onPageChange={onChange}
        />
        {data.page > 1 && !data.content.length && (
          <button className="of-button" onClick={() => onChange(1)}>
            Về trang đầu
          </button>
        )}
      </footer>
    )
  );
}
