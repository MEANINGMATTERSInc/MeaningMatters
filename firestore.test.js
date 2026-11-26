const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_author_001";
const BOB_UID = "bob_author_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user or book data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = pastDate();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("books").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("books")
      .doc("book_1")
      .set({
        userId: ALICE_UID,
        title: "Unauthorized Book",
        status: "CONCEPT",
        archived: false,
        wordCount: 0,
        targetWordCount: 50000,
        chapterCount: 0,
        completedChapters: 0,
        purposeApproved: false,
        ledgerApproved: false,
        pipelineStage: 1,
        driveSyncStatus: "LOCAL_ONLY",
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Owner can create and partially update UserProfile; rejects ghost fields & future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Sadman Sakib",
      email: "sadman@example.com",
      defaultPenName: "S. Sakib",
      editorFontSizeSp: 18,
      dyslexiaFriendlySpacing: false,
      focusModeDefault: false,
      autoSaveEnabled: true,
      preferredModel: "gemini-3.8-flash",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update preserves immutable createdAt & userId
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      editorFontSizeSp: 20,
      focusModeDefault: true,
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII read/write blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(500),
    })
  );

  // Shadow/ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future Edit",
      updatedAt: new Date(Date.now() + 120000),
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Owner full lifecycle on Book, Purpose, Voice, CanonicalLedger, Sections, Chapters, Versions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const bookRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("books")
    .doc("book_01");
  const created = pastDate(10000);

  // 1. Create Book (no 'id' field; mirrors @DocumentId)
  await assertSucceeds(
    bookRef.set({
      userId: ALICE_UID,
      title: "The Epistemic Architecture of Meaning",
      subtitle: "Purpose, Evidence, and Accountability",
      genre: "Philosophy & Systems",
      targetAudience: "Scholars and thoughtful builders",
      status: "CONCEPT",
      archived: false,
      wordCount: 0,
      targetWordCount: 60000,
      chapterCount: 0,
      completedChapters: 0,
      purposeApproved: false,
      ledgerApproved: false,
      pipelineStage: 1,
      lastCompletedSection: "",
      nextUnfinishedSection: "Stage 1: Canonical Ledger",
      driveSyncStatus: "LOCAL_ONLY",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject book with forbidden 'id' field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("books")
      .doc("book_bad")
      .set({
        id: "book_bad",
        userId: ALICE_UID,
        title: "Bad ID Field",
        status: "CONCEPT",
        archived: false,
        wordCount: 0,
        targetWordCount: 10000,
        chapterCount: 0,
        completedChapters: 0,
        purposeApproved: false,
        ledgerApproved: false,
        pipelineStage: 1,
        driveSyncStatus: "LOCAL_ONLY",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Partial update on Book
  await assertSucceeds(
    bookRef.update({
      status: "DRAFTING",
      purposeApproved: true,
      pipelineStage: 4,
      wordCount: 4200,
      updatedAt: pastDate(1000),
    })
  );

  // Owner list books succeeds; cross-user list fails
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("books").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("books").get()
  );

  // 2. BookPurpose
  await assertSucceeds(
    bookRef.collection("purpose").doc("primary").set({
      userId: ALICE_UID,
      bookId: "book_01",
      title: "The Epistemic Architecture of Meaning",
      subtitle: "Purpose, Evidence, and Accountability",
      genre: "Philosophy & Systems",
      intendedAudience: "Researchers and practitioners",
      centralPurpose: "Establish an accountable epistemic framework for writing.",
      mainThesis: "Meaning precedes generation and governs human-AI collaboration.",
      readerTransformation: "Readers gain rigorous tools for intellectual accountability.",
      authorValues: "Truthfulness, humility, mercy, rigor",
      intellectualBoundaries: "Never fabricate citations or claim certainty without evidence.",
      desiredStyle: "Academic yet accessible editorial prose",
      targetLengthWords: 60000,
      generatedDocument: "# Book Purpose Document\n...",
      approved: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // 3. AuthorVoice
  await assertSucceeds(
    bookRef.collection("voice").doc("primary").set({
      userId: ALICE_UID,
      bookId: "book_01",
      writingTone: "Reflective, disciplined, incisive",
      sentenceComplexity: "Balanced periodic and direct sentences",
      vocabularyLevel: "Scholarly yet lucid",
      narrativePerspective: "First-person plural (editorial we)",
      preferredTerms: ["epistemic", "accountability", "coherence"],
      avoidedTerms: ["synergy", "disruptive"],
      writingSamples: "Meaning cannot be retrofitted onto volume.",
      styleNotes: "Avoid hyperbole.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // 4. CanonicalLedger
  await assertSucceeds(
    bookRef.collection("ledger").doc("primary").set({
      userId: ALICE_UID,
      bookId: "book_01",
      coreThesis: "Purpose and epistemic governance must precede synthesis.",
      purpose: "Provide an auditable book architecture.",
      intendedAudience: "Serious readers",
      foundationalAxioms: ["A1: Capability does not confer authority."],
      epistemicStandards: "Classify every claim explicitly.",
      formalDefinitions: ["D1: Canonical Ledger = authoritative invariant store."],
      mathematicalSpecs: "C(t) = verified_claims / total_claims",
      terminologicalGlossary: ["Muhasabah: structured ethical self-accounting."],
      resolvedPremises: ["P1: Unverified claims must be labeled."],
      openQuestions: ["Q1: How do institutions sustain epistemic humility at scale?"],
      approved: true,
      version: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // 5. BookSection
  await assertSucceeds(
    bookRef.collection("sections").doc("sec_1").set({
      userId: ALICE_UID,
      bookId: "book_01",
      title: "Part I: Foundations of Meaning",
      description: "Establishes axioms and epistemic taxonomy.",
      orderIndex: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // 6. Chapter + 7-point completion ledger
  await assertSucceeds(
    bookRef.collection("chapters").doc("ch_1").set({
      userId: ALICE_UID,
      bookId: "book_01",
      sectionId: "sec_1",
      partLabel: "Part I",
      title: "Chapter 1: Why Purpose Precedes Generation",
      orderIndex: 0,
      objective: "Demonstrate why unanchored prose degrades trust.",
      coreProposition: "Meaning governs method.",
      argumentativePurpose: "Lay foundation for Part II.",
      requiredPremises: "Axiom A1",
      requiredEvidence: "Historical editorial standards",
      formalisms: "Authority Gate G(a) in {0,1}",
      strongestCounterargument: "Speed and volume accelerate discovery.",
      expectedResolution: "Discovery without verification compounds error.",
      transitionToNext: "Leads into epistemic classification in Chapter 2.",
      targetWordCount: 4000,
      content: "Every serious work begins with an account of its purpose...",
      wordCount: 9,
      status: "DRAFT",
      blueprintApproved: true,
      fullProseDrafted: false,
      argumentsDeveloped: false,
      evidenceReviewed: false,
      redTeamCompleted: false,
      revisionsIncorporated: false,
      continuityChecked: false,
      versionNumber: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on Chapter
  await assertSucceeds(
    bookRef.collection("chapters").doc("ch_1").update({
      fullProseDrafted: true,
      status: "REVIEWED",
      wordCount: 2500,
      updatedAt: pastDate(1000),
    })
  );

  // Value poisoning on Chapter status fails
  await assertFails(
    bookRef.collection("chapters").doc("ch_1").update({
      status: "INVALID_STATUS",
      updatedAt: pastDate(500),
    })
  );

  // 7. ManuscriptVersion (immutable once created)
  await assertSucceeds(
    bookRef.collection("versions").doc("ver_1").set({
      userId: ALICE_UID,
      bookId: "book_01",
      chapterId: "ch_1",
      versionNumber: 1,
      label: "First Draft Snapshot",
      content: "Every serious work begins with an account of its purpose...",
      wordCount: 9,
      authorApproved: false,
      createdAt: created,
    })
  );
  await assertFails(
    bookRef.collection("versions").doc("ver_1").update({
      label: "Tampered Snapshot",
    })
  );
});

test("Owner full lifecycle on Codex, Sources, Claims, Reviews, AI Logs, Exports & Cross-User Isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const bookRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("books")
    .doc("book_02");
  const created = pastDate(10000);

  // Codex entry
  await assertSucceeds(
    bookRef.collection("codex").doc("codex_1").set({
      userId: ALICE_UID,
      bookId: "book_02",
      title: "Muhasabah",
      category: "CULTURAL_TRADITION",
      summary: "Structured moral and intellectual self-accounting.",
      details: "Applied as a reflective editorial review discipline.",
      epistemicStatus: "TRADITION",
      originType: "AUTHOR_APPROVED",
      relatedChapterIds: ["ch_1"],
      approved: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Research source
  await assertSucceeds(
    bookRef.collection("sources").doc("src_1").set({
      userId: ALICE_UID,
      bookId: "book_02",
      title: "The Structure of Scientific Revolutions",
      author: "Thomas S. Kuhn",
      publicationDate: "1962",
      publisher: "University of Chicago Press",
      url: "https://press.uchicago.edu",
      sourceType: "Book",
      notes: "Foundational work on paradigm shifts.",
      evidenceLevel: "SUPPORTED",
      chapterId: "ch_1",
      citationText: "Kuhn, T. S. (1962). The Structure of Scientific Revolutions.",
      verificationNote: "Registered by author; not externally web-verified by app.",
      attachmentName: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Research claim
  await assertSucceeds(
    bookRef.collection("claims").doc("claim_1").set({
      userId: ALICE_UID,
      bookId: "book_02",
      chapterId: "ch_1",
      sourceId: "src_1",
      statement: "Paradigms shape which anomalies researchers notice.",
      evidenceLevel: "SUPPORTED",
      citationRef: "Kuhn (1962)",
      verified: true,
      notes: "Directly supported by Chapter VI.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Review Report (Muhasabah / RED_TEAM / Consistency / Whole-Book)
  await assertSucceeds(
    bookRef.collection("reviews").doc("rev_1").set({
      userId: ALICE_UID,
      bookId: "book_02",
      chapterId: "ch_1",
      reviewType: "MUHASABAH",
      summary: "Overall strong alignment with declared values; one overstatement noted.",
      overallScore: 88,
      findings: [
        {
          category: "Misleading Certainty",
          severity: "MEDIUM",
          chapterRef: "Chapter 1",
          description: "Claim presented as settled without empirical citation.",
          recommendation: "Reclassify as HYPOTHESIS or attach primary source.",
        },
      ],
      qualitiesFlagged: ["Arrogance"],
      unresolvedQuestions: ["Is this framing accessible to non-specialists?"],
      authorAcknowledged: false,
      createdAt: created,
    })
  );
  await assertSucceeds(
    bookRef.collection("reviews").doc("rev_1").update({
      authorAcknowledged: true,
    })
  );
  // Cannot tamper with review summary
  await assertFails(
    bookRef.collection("reviews").doc("rev_1").update({
      summary: "Tampered score",
      overallScore: 100,
    })
  );

  // AI Governance Interaction Log
  await assertSucceeds(
    bookRef.collection("ai_logs").doc("log_1").set({
      userId: ALICE_UID,
      bookId: "book_02",
      chapterId: "ch_1",
      agentRole: "Chapter Drafter",
      actionType: "DRAFT_CHAPTER",
      modelName: "gemini-3.8-flash",
      promptSummary: "Draft Chapter 1 from approved blueprint",
      outputSummary: "Drafted 1800 words preserving Canonical Ledger terms",
      decision: "PENDING",
      createdAt: created,
    })
  );
  await assertSucceeds(
    bookRef.collection("ai_logs").doc("log_1").update({
      decision: "ACCEPTED",
    })
  );

  // Export Job (immutable record)
  await assertSucceeds(
    bookRef.collection("exports").doc("exp_1").set({
      userId: ALICE_UID,
      bookId: "book_02",
      format: "EPUB",
      fileName: "epistemic_architecture.epub",
      status: "COMPLETED",
      validated: true,
      validationNotes: "Verified mimetype, container.xml, OPF, and XHTML chapters.",
      byteSize: 48210,
      chapterCount: 5,
      wordCount: 14200,
      createdAt: created,
    })
  );
  await assertFails(
    bookRef.collection("exports").doc("exp_1").update({
      validated: false,
    })
  );

  // Cross-user isolation across ALL subcollections
  const bobRef = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("books")
    .doc("book_02");
  await assertFails(bobRef.collection("codex").get());
  await assertFails(bobRef.collection("sources").get());
  await assertFails(bobRef.collection("claims").get());
  await assertFails(bobRef.collection("reviews").get());
  await assertFails(bobRef.collection("ai_logs").get());
  await assertFails(bobRef.collection("exports").get());
});
