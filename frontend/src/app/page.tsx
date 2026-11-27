import Link from "next/link";

export default function Home() {
  return (
    <section className="max-w-2xl">
      <h1 className="font-serif text-5xl font-semibold tracking-tight sm:text-7xl">
        BASE IELTS
      </h1>
      <p className="mt-6 max-w-md text-lg leading-relaxed text-[#41584e]">
        A learning space for teacher-led IELTS instruction and independent practice.
      </p>
      <Link href="/account" className="mt-9 inline-flex rounded-full bg-[#204d3f] px-6 py-3 font-semibold text-white">
        Account access
      </Link>
    </section>
  );
}
