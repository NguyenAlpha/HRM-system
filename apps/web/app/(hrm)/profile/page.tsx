import type { Metadata } from "next"

import { MyProfilePage } from "@/components/profile/my-profile-page"

export const metadata: Metadata = { title: "Hồ sơ của tôi" }

export default function ProfilePage() {
  return <MyProfilePage />
}
