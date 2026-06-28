import { redirect } from "next/navigation";
import { isSetupComplete, setAdminPassword, getSession } from "@/lib/auth";
import { logger } from "@/lib/log";
import SetupForm from "./SetupForm";

export const dynamic = "force-dynamic";

async function setupAction(prevState: string, formData: FormData): Promise<string> {
  "use server";
  if (isSetupComplete()) return "Server is already configured";
  const password = formData.get("password") as string;
  const confirm = formData.get("confirm") as string;
  if (!password || password.length < 8) return "Password must be at least 8 characters";
  if (password !== confirm) return "Passwords don't match";
  await setAdminPassword(password);
  const session = await getSession();
  session.authenticated = true;
  await session.save();
  logger.info("SETUP_COMPLETE");
  redirect("/");
}

export default async function SetupPage() {
  if (isSetupComplete()) redirect("/");
  return <SetupForm setupAction={setupAction} />;
}
