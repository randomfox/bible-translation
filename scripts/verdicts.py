"""Context verdicts for high-impact ambiguous readings (severity 3, and severity 2 where the
flag note records more than one possible sense).

For each case the surrounding text was read and the likeliest meaning chosen. The script
writes results/ambiguities.tsv and appends a short "Context:" verdict to the matching flag
note in data/compare/ (idempotent: an existing "Context:" sentence is replaced).

Confidence: high = the context points clearly one way; medium = it favours one reading but
the other stays possible; low = the context does not settle it.
"""
import csv, json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# (book, ref, question, verdict, confidence, evidence, changed)
V = [
 ("Genesis","3:5","like God, or like gods / divine beings","like God","medium","The first 'elohim in the verse is God; 3:22 'like one of us' confirms the knowledge is divine knowledge.","no"),
 ("Genesis","6:4","Nephilim: a name, 'giants' or 'fallen ones'","keep the name Nephilim; remembered as huge","medium","Num 13:33 links the Nephilim with the gigantic Anakim, so 'giants' reflects how Israel remembered them, but the word itself is a name.","no"),
 ("Genesis","10:11","Asshur went out, or he (Nimrod) went out to Assyria","Nimrod went to Assyria","high","10:8-12 is a single paragraph about Nimrod; Asshur the son of Shem appears separately at 10:22; Mic 5:6 calls Assyria 'the land of Nimrod'.","no"),
 ("Genesis","22:1","test, or tempt","test","high","22:12 'now I know that you fear God' states the purpose: a test of loyalty.","no"),
 ("Genesis","26:4","nations 'be blessed' (passive) or 'bless themselves' (reflexive)","bless themselves, i.e. invoke Abraham's offspring as a model of blessing","medium","The verb is hithpael here and at 22:18 (niphal at 12:3, 18:18, 28:14); Gen 48:20 shows the idiom: 'By you Israel will pronounce blessings, saying, God make you like Ephraim'.","no"),
 ("Genesis","37:3","coat of many colours, or long robe with sleeves","a long robe with sleeves","medium","The only other use (2 Sam 13:18-19) is the robe of the king's virgin daughters: a garment of status, described by its cut, not its colours.","no"),
 ("Genesis","49:5","habitations, or swords","swords","medium","The oracle recalls Gen 34:25, where Simeon and Levi 'took their swords' and killed the Shechemites.","no"),
 ("Genesis","49:10","Shiloh (place or name), or 'he to whom it belongs'","until he comes to whom it belongs","medium","The parallel line 'to him the obedience of the peoples' needs a person; Ezek 21:32 (Eng 21:27) echoes the phrase as 'until he comes to whom it belongs'. The town Shiloh plays no part in Judah's story.","no"),
 ("Exodus","6:3","God was not 'known' by the name Yahweh, though Genesis uses it","known in the character the name expresses","medium","6:6-7 'you shall know that I am Yahweh' when I redeem you: 'know' means experience the name's meaning through the exodus, not first hear the word.","no"),
 ("Exodus","15:2","I will praise him, or prepare him a habitation","I will praise him","high","The parallel line is 'my father's God, and I will exalt him'.","no"),
 ("Exodus","17:16","hand upon the throne of Yah (oath), or upon the banner of Yah","A hand upon the banner of Yah","medium","17:15 names the altar Yahweh-nissi 'Yahweh is my banner'; Moses' raised hands (17:11-12) were the sign of the battle. 'kes' is not otherwise a word for throne.","yes"),
 ("Exodus","21:6","bring him to God, or to the judges","to God","medium","22:8-9 uses the same phrase with a plural verb for God's verdict; the procedure takes place at the door, a sacral act.","no"),
 ("Exodus","21:22","her children come out alive (premature birth) or miscarry","open; a premature live birth is slightly favoured","low","'Come out' (yatsa) is used of live birth (Gen 25:25-26) and 'children' (yeladim) of living children; 'no harm' after the birth would be odd if the child had died. But whether the harm in 21:23 covers the child is not stated.","no"),
 ("Exodus","22:27","revile God, or revile the gods / judges","God","medium","The parallel line 'nor curse a ruler of your people' pairs divine and human authority.","no"),
 ("Exodus","39:34","badgers' skins, or a fine leather","a fine leather","medium","Ezek 16:10 uses tachash for luxury sandals; the material is valued, not a wild animal's pelt.","no"),
 ("Leviticus","16:8","for Azazel (name) or for the scapegoat","for Azazel","high","The two lots are 'one for Yahweh and one for Azazel': the parallel requires a recipient, not a description of the goat.","no"),
 ("Leviticus","17:11","atonement for the soul, or by means of the life","by means of the life","medium","The verse begins 'the life of the flesh is in the blood': the blood atones because it carries the life.","no"),
 ("Leviticus","19:20","she shall be scourged, or there shall be an inquiry / compensation","an inquiry (with compensation)","medium","The same verse says they shall not be put to death 'because she was not free', and 19:21 prescribes a guilt offering: a legal settlement, not a flogging.","no"),
 ("Leviticus","21:4","as a chief man, or as a husband","as a husband (i.e. not for in-laws)","medium","21:1-3 lists the blood relatives for whom a priest may become unclean; the wife's family are absent, and 21:4 excludes them.","no"),
 ("Leviticus","27:29","a person under the ban put to death, or a person vowed","a person under the ban of destruction","medium","27:28 deals with things devoted to Yahweh; 27:29 adds persons 'from among mankind' who 'shall surely be put to death', the war or judicial ban.","no"),
 ("Numbers","4:6","badgers' skins, or a fine leather","a fine leather","medium","As Exod 39:34.","no"),
 ("Numbers","4:20","see when covered, or look even for an instant","even for a moment","medium","4:15-19 forbid the Kohathites to touch or see the holy things on pain of death; 'as a swallowing' is an idiom for an instant.","no"),
 ("Numbers","13:33","giants, or the Nephilim","the Nephilim, seen as giants","medium","The spies add 'we seemed like grasshoppers': the name carries a sense of enormous size in their exaggerated report.","no"),
 ("Numbers","21:14","what he did at the Red Sea, or the places Waheb in Suphah","place names","medium","The quotation continues with 'the wadis of the Arnon' and the itinerary of 21:10-20: a list of places.","no"),
 ("Numbers","23:22","unicorn, or wild ox","wild ox","high","Deut 33:17 speaks of the 'horns' (plural) of a re'em, and Ps 22:22 of the horns of the wild oxen: a two-horned animal.","no"),
 ("Deuteronomy","4:30","if you turn (condition), or you will return (prediction)","you will return","high","4:29 'you will seek ... and you will find' and the parallel prophecy in 30:1-3 are predictions.","no"),
 ("Deuteronomy","6:4","Yahweh is one, or Yahweh alone","Yahweh is our God, Yahweh alone","medium","6:5 demands undivided love and 6:14-15 forbids other gods: the context is exclusive loyalty. The unity reading is grammatical and also ancient.","no"),
 ("Deuteronomy","23:3","bastard, or one born of a forbidden union","one born of a forbidden union","low","It stands beside the exclusion of Ammonites and Moabites; Zech 9:6 uses mamzer for a mixed population. The exact sense is unknown.","no"),
 ("Deuteronomy","26:5","a perishing Aramean, or a wandering Aramean","wandering","medium","The creed continues 'he went down into Egypt and sojourned there': the life of a migrant, recalling Jacob's years in Aram.","no"),
 ("Judges","5:2","for the avenging of Israel, or when the leaders led / let hair loose","when the leaders took the lead","medium","The parallel line is 'when the people offered themselves willingly', and 5:9 repeats it of 'the commanders of Israel'.","no"),
 ("Judges","5:5","mountains melted, or quaked","quaked","high","5:4 'the earth trembled, the heavens dropped' is a storm-and-earthquake theophany.","no"),
 ("Judges","12:14","nephews, or grandsons","grandsons","high","'Sons of sons'; not ambiguous in Hebrew, only in modern English.","no"),
 ("1 Samuel","28:13","gods, or a divine being","a divine being","high","28:14 Saul asks 'What is his appearance?' and is told 'an old man is coming up': one figure.","no"),
 ("2 Samuel","1:18","teach the use of the bow, or teach 'The Bow' (a title)","a song title","medium","The verse continues 'it is written in the Book of Jashar' and the lament follows at once; nothing in the passage concerns archery.","no"),
 ("2 Samuel","21:19","Elhanan killed Goliath, or the brother of Goliath","Goliath (as the Hebrew says)","high","The list in 21:15-22 records giants killed by David's men; the KJV's 'brother of' comes from 1 Chr 20:5, not from this text. The tension with 1 Sam 17 is real and is left in place.","no"),
 ("1 Kings","10:5","his ascent to the house of Yahweh, or the burnt offerings he offered","burnt offerings","low","The verse is a list of court spectacles; 2 Chr 9:4 reads 'his ascent'. Both fit the list; the verb 'offered up' favours offerings slightly.","no"),
 ("2 Kings","23:29","went up against, or went up to (help) the king of Assyria","went up to the king of Assyria","high","The Babylonian Chronicle shows Egypt marched to support Assyria at Harran in 609; Josiah intercepted Neco, so the Egyptians were Assyria's allies.","no"),
 ("1 Chronicles","17:17","you regard me as a man of high degree, or shown me future generations","you have shown me (the line of) future generations","low","The parallel 2 Sam 7:19 reads differently ('this is the instruction for mankind'); the verse concerns the dynasty 'for a great while to come'. Unresolved.","no"),
 ("1 Chronicles","21:1","Satan, or an adversary","Satan","medium","Without the article the noun behaves as a proper name (Job and Zechariah have 'the satan', a title). The figure incites the king where 2 Sam 24:1 has God doing so, and no human adversary appears in the story.","yes (flag removed: now agrees with the KJV)"),
 ("Job","13:15","yet will I trust in him (qere), or I have no hope (ketiv)","I have no hope (of surviving), yet I will argue my case","medium","13:14 'I will put my life in my hand' expects death; 13:15b 'yet I will argue my ways to his face' and 13:16-18 'I know I shall be vindicated' make defiance with hope of vindication, not trust in being spared.","no"),
 ("Job","19:26","in my flesh, or apart from my flesh","seeing God bodily ('from my flesh')","medium","19:27 'my eyes shall behold, and not another' stresses embodied sight. Whether this is after death or a vindication before death remains open.","no"),
 ("Job","42:6","I abhor myself and repent, or I retract and am comforted","I retract my words and am comforted","medium","42:7-8 God says the friends have not spoken rightly 'as my servant Job has' and accepts Job's prayer: Job is not being made to confess guilt.","no"),
 ("Psalms","10:3","blesses the greedy and renounces Yahweh, or the greedy curses and renounces Yahweh","the greedy man curses and renounces Yahweh","low","10:4 'all his thoughts are, There is no God' supports renouncing God; the verb barakh may be a euphemism for cursing (as in Job 1:5). The syntax remains difficult.","no"),
 ("Psalms","22:17","they pierced (versions), or like a lion (MT)","like a lion (the verb is implied)","medium","The poem pairs its beasts in a chiasm: bulls (v.13), lion (v.14), dogs (v.17) / dog (v.21), lion and wild oxen (v.22). 'Like a lion' at v.17 completes the pattern. The line lacks a verb, which is why the ancient versions read one.","no"),
 ("Psalms","58:2","O congregation (or silence), or you gods / mighty ones","you gods (rulers)","medium","58:2b 'your hands deal out violence on earth' addresses rulers who judge; compare Ps 82, where the 'gods' are judges condemned for injustice.","no"),
 ("Proverbs","26:10","God who created all rewards the fool, or an archer who wounds everyone","an archer wounding everyone is like one hiring a fool","low","26:1-12 is a string of fool proverbs; the line is so obscure that no reading is secure.","no"),
 ("Proverbs","30:1","names (Ithiel, Ucal), or 'I am weary, O God'","I am weary, O God","medium","30:2-3 'I am more brutish than any man ... I have not learned wisdom' continues a confession of exhaustion.","no"),
 ("Ecclesiastes","2:8","musical instruments, or concubines","concubines","medium","The list ends 'the delights of the sons of man', after singers; a harem fits the catalogue of a king's pleasures.","no"),
 ("Ecclesiastes","3:21","the spirit goes up (statement), or whether it goes up (question)","a question: who knows whether","high","3:19-20 'all go to one place, all are from dust' and 3:22 'who can bring him to see what will be after him?' are sceptical.","no"),
 ("Isaiah","7:14","a virgin will conceive, or the young woman is pregnant","the young woman is pregnant (a sign in Ahaz's own time)","high","7:15-16 'before the boy knows how to refuse evil ... the land whose two kings you dread will be deserted' sets the sign within a few years; 8:3-4 gives a parallel birth-sign. Matthew's later use of the Greek text is a separate matter.","no"),
 ("Isaiah","14:12","Lucifer (a name), or Shining One, son of Dawn","Shining One, son of Dawn, said of the king of Babylon","high","14:4 introduces the taunt 'against the king of Babylon'; 14:16 'is this the man who made the earth tremble?'","no"),
 ("Isaiah","18:2","scattered and peeled, or tall and smooth","tall and smooth-skinned","medium","The oracle addresses Cush (18:1); Herodotus describes the Ethiopians as the tallest and handsomest of peoples.","no"),
 ("Isaiah","28:10","precept upon precept, or mocking babble","babble (tsav la-tsav, qav la-qav)","high","28:9 the drunk priests mock Isaiah as talking baby-talk; 28:11 'by people of strange lips and a foreign tongue' turns their mockery into judgment.","no"),
 ("Ezekiel","21:32","until he comes whose right it is (messianic), or to whom judgment is given","the one to whom judgment is committed, Babylon's king","medium","The oracle removes Zedekiah's crown (21:30-31); 23:24 'I will commit judgment to them, and they shall judge you' uses the same word for the Babylonians. The echo of Gen 49:10 is ironic.","no"),
 ("Ezekiel","22:16","you shall take your inheritance in yourself, or be profaned by your own doing","you shall be profaned","medium","22:15 'I will scatter you among the nations': the shame of exile 'in the sight of the nations'.","no"),
 ("Daniel","9:25","69 weeks to the Messiah, or 7 weeks to an anointed prince, then 62 weeks of rebuilding","7 weeks to an anointed one; then 62 weeks","medium","9:26 places a second anointed one 'cut off' after the 62 weeks, a separate event; the Masoretic accent also divides after 'seven weeks'.","no"),
 ("Malachi","2:16","I (God) hate divorce, or the man who hates and divorces","the man who hates and divorces covers his garment with violence","medium","2:14-15 accuse husbands of faithlessness to 'the wife of your youth', and 2:16 ends 'do not be faithless': the subject is the man.","no"),
 ("Matthew","27:16","Barabbas, or Jesus Barabbas","Jesus Barabbas","medium","27:17 Pilate offers 'Jesus Barabbas or Jesus who is called Christ': 'who is called Christ' only makes sense if both men are named Jesus.","no"),
 ("Mark","1:41","moved with compassion, or moved with anger","anger","medium","1:43 'he sternly warned him and drove him out' shows strong emotion in the same scene; scribes are more likely to have softened anger than invented it.","no"),
 ("Mark","16:9","is 16:9-20 original?","a later addition","high","16:9 reintroduces Mary Magdalene as if new, though she was named in 16:1, and the subject shifts awkwardly from the women; vocabulary and style differ from Mark.","no"),
 ("Luke","23:34","is 'Father, forgive them' original?","open; Luke's own style supports it","low","Luke favours prayers to 'Father' (23:46) and parallels this prayer in Stephen's death (Acts 7:60), which supports authenticity; but early manuscripts lack it.","no"),
 ("John","7:53","is the woman taken in adultery original?","a later addition","high","7:52 flows directly into 8:12 'Again Jesus spoke to them'; the passage's vocabulary is Lukan rather than Johannine.","no"),
 ("1 John","5:7","the heavenly witnesses, or three that testify","the Spirit, water and blood only","high","5:6 has just named the Spirit, water and blood, and 5:8-9 continue that argument; the heavenly witnesses break it.","no"),
 ("Revelation","22:14","do his commandments, or wash their robes","wash their robes","high","7:14 'they have washed their robes and made them white in the blood of the Lamb' is the book's own image.","no"),
 ("Revelation","22:19","book of life, or tree of life","tree of life","high","22:2 and 22:14 speak of the tree of life; the prophecy's closing warning refers back to it.","no"),
]


def main():
    books = {b["name"]: b["dir"] for b in json.loads((ROOT / "data" / "books.json").read_text())}
    out = ROOT / "results" / "ambiguities.tsv"
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(["book", "ref", "question", "verdict", "confidence", "context", "rendering_changed"])
        for row in V:
            w.writerow(row)
    hit = 0
    for book, ref, q, verdict, conf, ev, changed in V:
        d = ROOT / "data" / "compare" / books[book]
        ch = int(ref.split(":")[0])
        p = d / f"{ch:03d}.tsv"
        if not p.exists():
            continue
        lines = p.read_text(encoding="utf-8").splitlines()
        for i, l in enumerate(lines):
            f = l.split("\t")
            if f[0] == ref and len(f) == 6:
                note = re.sub(r" Context: .*$", "", f[5])
                f[5] = f"{note} Context: {verdict} ({conf} confidence). {ev}"
                lines[i] = "\t".join(f)
                hit += 1
                break
        p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(len(V), "verdicts;", hit, "flag notes annotated ->", out.relative_to(ROOT))


if __name__ == "__main__":
    main()
