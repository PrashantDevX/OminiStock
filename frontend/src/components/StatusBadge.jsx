export default function StatusBadge({ status }) {
  const value = String(status || '').toUpperCase()
  return <span className={`badge badge-${value.toLowerCase()}`}>{value}</span>
}
