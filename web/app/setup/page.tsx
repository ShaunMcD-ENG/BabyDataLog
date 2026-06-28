import { redirect } from "next/navigation";
import { isSetupComplete } from "@/lib/auth";
import SetupForm from "./SetupForm";

export const dynamic = "force-dynamic";

export default async function SetupPage() {
  if (isSetupComplete()) redirect("/");
  return <SetupForm />;
}
