import { redirect } from "next/navigation";
import { isAuthenticated, isSetupComplete } from "@/lib/auth";
import LoginForm from "./LoginForm";

export default async function LoginPage() {
  if (!isSetupComplete()) redirect("/setup");
  if (await isAuthenticated()) redirect("/");
  return <LoginForm />;
}
