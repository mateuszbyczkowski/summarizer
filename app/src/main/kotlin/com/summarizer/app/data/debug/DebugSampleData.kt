package com.summarizer.app.data.debug

import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.util.ThreadNaming
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug builds only: a fixed sample chat for comparing models and prompts on the same input.
 * Synthetic content (no real people). Re-created when missing, e.g. after a reset or retention.
 * Remove once the app leaves development.
 */
@Singleton
class DebugSampleData @Inject constructor(
    private val messageRepository: MessageRepository
) {

    companion object {
        const val THREAD_NAME = "🧪 Test: Klasa 3B – rodzice"
        private const val MINUTE = 60_000L
    }

    private data class Sample(val minutesAgo: Long, val sender: String, val text: String, val type: MessageType = MessageType.TEXT)

    private val samples = listOf(
        Sample(1500, "Anna (wychowawczyni)", "Dzień dobry Państwu! Przypominam, że w piątek 17.10 jedziemy na wycieczkę do Centrum Nauki Kopernik."),
        Sample(1495, "Anna (wychowawczyni)", "Zbiórka o 7:45 przy głównej bramie szkoły, autobus odjeżdża punktualnie o 8:00. Powrót ok. 16:30."),
        Sample(1480, "Marek", "Dzień dobry, ile wynosi koszt wycieczki?"),
        Sample(1478, "Anna (wychowawczyni)", "Koszt to 85 zł od dziecka (bilet, autobus i ubezpieczenie). Proszę o przelew na konto klasowe do środy 15.10."),
        Sample(1470, "Kasia", "Czy dzieci mają zabrać prowiant?"),
        Sample(1468, "Anna (wychowawczyni)", "Tak, drugie śniadanie i coś do picia. Obiadu nie przewidujemy."),
        Sample(1450, "Tomek", "A zgody? Mamy je drukować same?"),
        Sample(1445, "Anna (wychowawczyni)", "Zgody wyślę jutro w Librusie. Proszę je podpisać i oddać do poniedziałku."),
        Sample(1440, "Kasia", "Mogę zebrać zgody w poniedziałek rano przy wejściu, jeśli to pomoże 🙂"),
        Sample(1438, "Anna (wychowawczyni)", "Bardzo dziękuję, Kasiu! To duża pomoc."),
        Sample(1300, "Ola", "Hej, czy ktoś wie, czy w czwartek są zajęcia z basenu?"),
        Sample(1290, "Marek", "Z tego co wiem, basen w tym tygodniu odwołany, bo jest przegląd techniczny."),
        Sample(1285, "Anna (wychowawczyni)", "Potwierdzam, w czwartek 16.10 zamiast basenu dzieci mają zajęcia w sali gimnastycznej. Proszę o strój sportowy."),
        Sample(1100, "Tomek", "📷 Photo", MessageType.IMAGE),
        Sample(1098, "Tomek", "Znalazłem w szatni niebieską bluzę z napisem NASA, czyja?"),
        Sample(1090, "Ola", "To chyba Zosi! Dzięki, odbiorę jutro."),
        Sample(900, "Piotr", "This message was deleted", MessageType.DELETED),
        Sample(880, "Piotr", "Przepraszam, pomyłka. Pytanie: czy zebranie rodziców jest online czy w szkole?"),
        Sample(870, "Anna (wychowawczyni)", "Zebranie odbędzie się we wtorek 21.10 o 18:00 w szkole, sala 12. Tematy: wyniki śródrocznych testów i plan jasełek."),
        Sample(860, "Kasia", "Czy mogę przyjść z młodszym dzieckiem?"),
        Sample(858, "Anna (wychowawczyni)", "Oczywiście, nie ma problemu."),
        Sample(600, "Marek", "Przypominam o składce na radę rodziców – 50 zł do końca października."),
        Sample(590, "Ola", "Czy można zapłacić gotówką na zebraniu?"),
        Sample(585, "Marek", "Tak, gotówką na zebraniu albo przelewem, jak komu wygodniej."),
        Sample(300, "Anna (wychowawczyni)", "Pilne: zmiana! Autobus na wycieczkę odjedzie o 7:30, więc zbiórka już o 7:15. Przepraszam za zamieszanie."),
        Sample(295, "Tomek", "Ok, dzięki za info!"),
        Sample(290, "Kasia", "Przyjęłam 👍"),
        Sample(120, "Ola", "Czy ktoś mógłby w piątek zabrać Zosię na zbiórkę? Ja zaczynam pracę o 7:00."),
        Sample(110, "Marek", "Mogę ją zabrać, będę jechał z Kubą o 7:00 spod bloku na Słonecznej."),
        Sample(100, "Ola", "Dziękuję bardzo, Marku! ❤️")
    )

    suspend fun seed() {
        // Fixed hashes keep re-seeding idempotent; timestamps are relative to the first seed
        sampleMessages(System.currentTimeMillis()).forEach { messageRepository.saveIncomingMessage(it) }
    }

    /** The sample chat as messages ending shortly before [now]; also used by the benchmark. */
    fun sampleMessages(now: Long): List<Message> {
        val threadId = ThreadNaming.threadIdFor(THREAD_NAME)
        return samples.mapIndexed { index, sample ->
            Message(
                threadId = threadId,
                threadName = THREAD_NAME,
                sender = sample.sender,
                content = sample.text,
                timestamp = now - sample.minutesAgo * MINUTE,
                messageHash = "debug-sample-$index",
                messageType = sample.type,
                isDeleted = sample.type == MessageType.DELETED
            )
        }
    }
}
