import { redirect } from "next/navigation";
import { isSetupComplete } from "@/lib/auth";
import SetupForm from "./SetupForm";

export default async function SetupPage() {
  if (isSetupComplete()) redirect("/");
  return <SetupForm />;
}
