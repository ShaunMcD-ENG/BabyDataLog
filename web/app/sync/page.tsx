import { redirect } from "next/navigation";

// /sync now redirects to the main dashboard
export default function SyncPage() {
  redirect("/");
}
