# The Claude Translation compared with the BSB

Made by `scripts/compare_bsb.py` from `bible.sqlite` and the Berean Standard Bible text from eBible.org, with the KJV as a reference point. Per-verse scores are in `bsb-scores.tsv`. The KJV differences themselves are recorded verse by verse in `flags.tsv` and `kjv-comparison.html`; no such review has been done against the BSB, so this page measures rather than explains.

Verses compared: 31,085, every verse the three texts share in KJV numbering.

## Similarity

Each score runs from 0 (nothing shared) to 1 (identical). *Wording* compares the words in order, so word choice, word order and "Yahweh" against "the LORD" all lower it. *Content* is the measure `report.py` uses: it ignores common words, word order and archaic forms, and treats Yahweh and LORD as the same, so it shows whether verses say the same things.

| | CT ~ BSB | CT ~ KJV | BSB ~ KJV |
|---|---|---|---|
| Whole Bible, wording | **0.697** | 0.635 | 0.546 |
| Old Testament, wording | **0.688** | 0.639 | 0.549 |
| New Testament, wording | **0.724** | 0.626 | 0.539 |
| Whole Bible, content | **0.721** | 0.658 | 0.569 |
| Old Testament, content | **0.717** | 0.669 | 0.575 |
| New Testament, content | **0.733** | 0.627 | 0.551 |

Share of verses where the CT and the BSB reach each wording score:

| at least | 0.9 | 0.7 | 0.5 | 0.3 |
|---|---|---|---|---|
| verses | 8.7% | 52.3% | 90.1% | 99.0% |

## Which verses each leaves out

Of the KJV's 31,102 verses, the CT has no text for 16 and the BSB for 16. Both leave out the same 15: MAT 17:21, MAT 18:11, MAT 23:14, MAR 7:16, MAR 9:44, MAR 9:46, MAR 11:26, MAR 15:28, LUK 17:36, LUK 23:17, JOH 5:4, ACT 8:37, ACT 15:34, ACT 24:7, ACT 28:29.

Only the CT leaves out NEH 7:68; its Hebrew or Greek source has no such verse.
Only the BSB leaves out ROM 16:24.

## Word choices

How often each term appears in each text.

| Term | CT | BSB | KJV |
|---|---|---|---|
| Yahweh | 6,846 | 0 | 0 |
| LORD (small capitals in print) | 0 | 6,487 | 6,570 |
| Holy Spirit | 93 | 94 | 1 |
| Holy Ghost | 0 | 0 | 89 |
| Christ | 532 | 547 | 571 |
| Messiah | 2 | 4 | 2 |
| Sheol | 66 | 66 | 0 |
| Hades | 10 | 10 | 0 |
| hell | 1 | 13 | 54 |
| steadfast love | 201 | 1 | 0 |
| loving devotion | 0 | 188 | 0 |
| lovingkindness / mercy | 146 | 139 | 302 |
| slave(s) | 200 | 111 | 2 |
| servant(s) | 963 | 946 | 980 |
| saints | 61 | 86 | 96 |
| holy ones | 18 | 9 | 1 |
| behold | 694 | 326 | 1,326 |
| truly / verily | 141 | 138 | 182 |
| thee / thou / thy / thine | 0 | 0 | 14,841 |
| He / Him / His mid-sentence | 6,272 | 5,442 | 99 |

## The 13 major KJV differences

The comparison with the KJV marked 13 differences as changing what a passage says. This is where the BSB stands on each, judged by reading the three texts.

| Passage | KJV | CT | BSB | BSB sides with |
|---|---|---|---|---|
| GEN 49:10 | until Shiloh come; and unto him shall the gathering of the people be | until the One to Whom it belongs comes, and the obedience of the peoples is His | The scepter will not depart from Judah, nor the staff from between his feet, until Shiloh comes and the allegiance of the nations is his. | KJV |
| DEU 6:4 | The LORD our God [is] one LORD | Yahweh our God, Yahweh is one | Hear, O Israel: The LORD our God, the LORD is One. | CT |
| 2SA 21:19 | slew [the brother of] Goliath the Gittite | struck down Goliath the Gittite | Once again there was a battle with the Philistines at Gob, and Elhanan son of Jair the Bethlehemite killed the brother of Goliath the Gittite, the shaft of whose spear was like a weaver’s beam. | KJV |
| JOB 19:26 | And [though] after my (Job's) skin [worms] destroy this [body], yet in my flesh shall I see God | And after my (Job's) skin has been thus destroyed, yet from my flesh I shall see God | Even after my skin has been destroyed, yet in my flesh I will see God. | CT |
| JOB 42:6 | Wherefore I (Job) abhor [myself], and repent in dust and ashes | I (Job) reject my words and repent | Therefore I retract my words, and I repent in dust and ashes.” | CT |
| ISA 7:14 | a virgin shall conceive | the young woman is pregnant | Therefore the Lord Himself will give you a sign: Behold, the virgin will be with child and will give birth to a son, and will call Him Immanuel. | KJV |
| ISA 14:12 | O Lucifer, son of the morning | O Shining One, son of Dawn | How you have fallen from heaven, O day star, son of the dawn! You have been cut down to the ground, O destroyer of nations. | CT |
| DAN 9:25 | unto the Messiah the Prince [shall be] seven weeks, and threescore and two weeks | until an anointed one, a prince, there shall be seven weeks. Then for sixty-two weeks it (Jerusalem) shall be built again | Know and understand this: From the issuance of the decree to restore and rebuild Jerusalem, until the Messiah, the Prince, there will be seven weeks and sixty-two weeks. It will be rebuilt with streets and a trench, but in times of distress. | KJV |
| MAR 16:9 | Now when Jesus was risen early ... (16:9-20, printed as part of Mark without comment) | [[16:9-20 in double brackets]] | prints 16:9–20 with no brackets | KJV |
| JOH 7:53 | And every man went unto his own house. | [[And each went to his own house. | prints 7:53–8:11 with no brackets | KJV |
| 1JO 5:7 | For there are three that bear record in heaven, the Father, the Word, and the Holy Ghost: and these three are one. And there are three that bear witness in earth | For there are three that testify: the Spirit and the water and the blood | For there are three that testify: | CT |
| REV 22:14 | Blessed are they that do his commandments | Blessed are those who wash their robes | Blessed are those who wash their robes, so that they may have the right to the tree of life and may enter the city by its gates. | CT |
| REV 22:19 | God shall take away his part out of the book of life | God will take away his share in the tree of life | And if anyone takes away from the words of this book of prophecy, God will take away his share in the tree of life and the holy city, which are described in this book. | CT |

The BSB sides with the CT on 7 and with the KJV on 6.

## By book

Wording scores.

| Book | Verses | CT ~ BSB | CT ~ KJV | BSB ~ KJV |
|---|---|---|---|---|
| Genesis | 1,533 | 0.714 | 0.662 | 0.546 |
| Exodus | 1,213 | 0.690 | 0.622 | 0.531 |
| Leviticus | 859 | 0.659 | 0.601 | 0.501 |
| Numbers | 1,288 | 0.658 | 0.626 | 0.509 |
| Deuteronomy | 959 | 0.684 | 0.581 | 0.514 |
| Joshua | 658 | 0.713 | 0.650 | 0.559 |
| Judges | 618 | 0.679 | 0.654 | 0.546 |
| Ruth | 85 | 0.673 | 0.659 | 0.529 |
| 1 Samuel | 810 | 0.688 | 0.666 | 0.559 |
| 2 Samuel | 695 | 0.707 | 0.676 | 0.570 |
| 1 Kings | 816 | 0.699 | 0.687 | 0.575 |
| 2 Kings | 719 | 0.707 | 0.712 | 0.605 |
| 1 Chronicles | 942 | 0.737 | 0.705 | 0.626 |
| 2 Chronicles | 822 | 0.691 | 0.709 | 0.603 |
| Ezra | 280 | 0.701 | 0.674 | 0.568 |
| Nehemiah | 405 | 0.727 | 0.690 | 0.585 |
| Esther | 167 | 0.631 | 0.662 | 0.526 |
| Job | 1,070 | 0.654 | 0.614 | 0.490 |
| Psalms | 2,461 | 0.707 | 0.602 | 0.566 |
| Proverbs | 915 | 0.694 | 0.588 | 0.538 |
| Ecclesiastes | 222 | 0.651 | 0.629 | 0.519 |
| Song of Songs | 117 | 0.721 | 0.643 | 0.568 |
| Isaiah | 1,292 | 0.661 | 0.617 | 0.524 |
| Jeremiah | 1,364 | 0.677 | 0.644 | 0.565 |
| Lamentations | 154 | 0.698 | 0.568 | 0.526 |
| Ezekiel | 1,273 | 0.672 | 0.627 | 0.528 |
| Daniel | 357 | 0.672 | 0.684 | 0.544 |
| Hosea | 197 | 0.689 | 0.609 | 0.551 |
| Joel | 73 | 0.696 | 0.673 | 0.600 |
| Amos | 146 | 0.667 | 0.661 | 0.547 |
| Obadiah | 21 | 0.662 | 0.582 | 0.523 |
| Jonah | 48 | 0.634 | 0.678 | 0.551 |
| Micah | 105 | 0.683 | 0.628 | 0.542 |
| Nahum | 47 | 0.624 | 0.494 | 0.464 |
| Habakkuk | 56 | 0.641 | 0.540 | 0.486 |
| Zephaniah | 53 | 0.652 | 0.569 | 0.574 |
| Haggai | 38 | 0.701 | 0.672 | 0.641 |
| Zechariah | 211 | 0.696 | 0.607 | 0.568 |
| Malachi | 55 | 0.730 | 0.531 | 0.572 |
| Matthew | 1,068 | 0.754 | 0.655 | 0.569 |
| Mark | 673 | 0.700 | 0.648 | 0.520 |
| Luke | 1,149 | 0.725 | 0.660 | 0.542 |
| John | 878 | 0.747 | 0.655 | 0.556 |
| Acts | 1,003 | 0.677 | 0.629 | 0.509 |
| Romans | 432 | 0.732 | 0.588 | 0.543 |
| 1 Corinthians | 437 | 0.727 | 0.586 | 0.523 |
| 2 Corinthians | 257 | 0.678 | 0.524 | 0.484 |
| Galatians | 149 | 0.739 | 0.602 | 0.544 |
| Ephesians | 155 | 0.735 | 0.653 | 0.581 |
| Philippians | 104 | 0.709 | 0.590 | 0.545 |
| Colossians | 95 | 0.715 | 0.591 | 0.542 |
| 1 Thessalonians | 89 | 0.733 | 0.609 | 0.530 |
| 2 Thessalonians | 47 | 0.733 | 0.622 | 0.561 |
| 1 Timothy | 113 | 0.697 | 0.526 | 0.498 |
| 2 Timothy | 83 | 0.732 | 0.553 | 0.535 |
| Titus | 46 | 0.690 | 0.535 | 0.485 |
| Philemon | 25 | 0.761 | 0.523 | 0.490 |
| Hebrews | 303 | 0.704 | 0.589 | 0.517 |
| James | 108 | 0.706 | 0.591 | 0.496 |
| 1 Peter | 105 | 0.693 | 0.555 | 0.502 |
| 2 Peter | 61 | 0.640 | 0.540 | 0.493 |
| 1 John | 105 | 0.817 | 0.690 | 0.647 |
| 2 John | 13 | 0.779 | 0.605 | 0.564 |
| 3 John | 14 | 0.708 | 0.490 | 0.490 |
| Jude | 25 | 0.693 | 0.529 | 0.487 |
| Revelation | 404 | 0.773 | 0.631 | 0.589 |

## Least alike

The 20 verses where the CT and the BSB share the least content, leaving out verses with figures (one may write 8,580 where the other spells it out) and verses under ten words. Most are the same sense in different words, or a different reading of a hard Hebrew verse.

- **GEN 10:13** (content 0.00)
  - CT: Egypt fathered the Ludim, the Anamim, the Lehabim, the Naphtuhim,
  - BSB: Mizraim was the father of the Ludites, the Anamites, the Lehabites, the Naphtuhites,
- **2KI 22:7** (content 0.00)
  - CT: But no accounting shall be asked of them for the silver handed over to them, for they deal honestly."
  - BSB: But they need not account for the money put into their hands, since they work with integrity.”
- **JOB 6:17** (content 0.00)
  - CT: When they melt, they disappear; when it is hot, they vanish from their place.
  - BSB: but ceasing in the dry season and vanishing from their channels in the heat.
- **JOB 30:5** (content 0.00)
  - CT: They are driven out from human company; they shout after them as after a thief.
  - BSB: They were banished from among men, shouted down like thieves,
- **PSA 18:36** (content 0.00)
  - CT: You gave a wide place for my steps under me, and my feet did not slip.
  - BSB: You broaden the path beneath me so that my ankles do not give way.
- **PRO 7:21** (content 0.00)
  - CT: With much seductive speech she persuades him; with her smooth talk she compels him.
  - BSB: With her great persuasion she entices him; with her flattering lips she lures him.
- **ISA 19:10** (content 0.00)
  - CT: Those who are the pillars of the land will be crushed, and all who work for pay will be grieved.
  - BSB: The workers in cloth will be dejected, and all the hired workers will be sick at heart.
- **LAM 3:63** (content 0.00)
  - CT: Behold their sitting and their rising; I am the object of their taunt-songs.
  - BSB: When they sit and when they rise, see how they mock me in song.
- **EZE 41:21** (content 0.00)
  - CT: The doorposts of the nave were squared, and in front of the Holy Place was something resembling
  - BSB: The outer sanctuary had a rectangular doorframe, and the doorframe of the sanctuary was similar.
- **1CO 10:23** (content 0.00)
  - CT: "All things are lawful," but not all things are helpful. "All things are lawful," but not all things build up.
  - BSB: “Everything is permissible,” but not everything is beneficial. “Everything is permissible,” but not everything is edifying.
- **2CO 6:12** (content 0.00)
  - CT: You are not restricted by us, but you are restricted in your own affections.
  - BSB: It is not our affection, but yours, that is restrained.
- **ROM 12:8** (content 0.06)
  - CT: the one who exhorts, in his exhortation; the one who contributes, in generosity; the one who leads, with zeal; the one who does acts of mercy, with cheerfulness.
  - BSB: if it is encouraging, let him encourage; if it is giving, let him give generously; if it is leading, let him lead with diligence; if it is showing mercy, let him do it cheerfully.
- **JOB 36:18** (content 0.09)
  - CT: Beware lest wrath entice you into scoffing, and let not the greatness of the ransom turn you aside.
  - BSB: Be careful that no one lures you with riches; do not let a large bribe lead you astray.
- **NUM 7:9** (content 0.10)
  - CT: But to the sons of Kohath he gave none, because they were in charge of the service of the holy things, which had to be carried on the shoulder.
  - BSB: But he did not give any to the Kohathites, since they were to carry on their shoulders the holy objects for which they were responsible.
- **JER 18:16** (content 0.10)
  - CT: making their land a horror, a thing to be hissed at forever. Everyone who passes by it is horrified and shakes his head.
  - BSB: They have made their land a desolation, a perpetual object of scorn; all who pass by will be appalled and shake their heads.
- **JDG 15:7** (content 0.11)
  - CT: Samson said to them, "If this is how you act, I swear I will take revenge on you, and after that I will stop."
  - BSB: And Samson told them, “Because you have done this, I will not rest until I have taken vengeance upon you.”
- **JOB 8:12** (content 0.11)
  - CT: While yet in flower and not cut down, they wither before any other plant.
  - BSB: While the shoots are still uncut, they dry up quicker than grass.
- **PRO 17:7** (content 0.11)
  - CT: Fine speech is not becoming to a fool; still less is false speech to a noble.
  - BSB: Eloquent words are unfit for a fool; how much worse are lying lips to a ruler!
- **TIT 3:10** (content 0.11)
  - CT: As for a person who stirs up division, after warning him once and then twice, have nothing more to do with him,
  - BSB: Reject a divisive man after a first and second admonition,
- **PSA 39:2** (content 0.11)
  - CT: I was mute and silent; I held my peace to no avail, and my distress grew worse.
  - BSB: I was speechless and still; I remained silent, even from speaking good, and my sorrow was stirred.

The BSB text is from eBible.org's edition, which has no footnotes or brackets.
