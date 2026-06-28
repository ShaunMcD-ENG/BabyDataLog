import { redirect } from "next/navigation";
import { isSetupComplete, isAuthenticated, verifyAdminPassword, getSession } from "@/lib/auth";
import LoginForm from "./LoginForm";

export const dynamic = "force-dynamic";

async function loginAction(prevState: string, formData: FormData): Promise<string> {
  "use server";
  const password = formData.get("password") as string;
  if (!password) return "Password is required";
  const valid = await verifyAdminPassword(password);
  if (!valid) return "Incorrect password";
  const session = await getSession();
  session.authenticated = true;
  await session.save();
  redirect("/");
}

export default async function LoginPage() {
  if (!isSetupComplete()) redirect("/setup");
  if (await isAuthenticated()) redirect("/");
  return <LoginForm loginAction={loginAction} />;
}
