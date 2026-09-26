package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AdminQuestionSortCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiPronunciationRequestDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiPronunciationResponseDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.OriginalQuestionDTO;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
import io.github.mawsonlakes790913.chineseoutputforge.form.QuestionForm;
import io.github.mawsonlakes790913.chineseoutputforge.repository.FavoriteRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理者用の問題管理に関する業務処理を行うService。
 * 問題の検索・追加・編集・削除を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminQuestionService {

    private final StructureRepository structureRepository;
    private final SearchConditionConverter searchConditionConverter;
    private final QuestionRepository questionRepository;
    private final FavoriteRepository favoriteRepository;
    private final StudyHistoryRepository studyHistoryRepository;
    private final AiPronunciationService aiPronunciationService;

    /**
     * 指定された検索条件に一致する管理者用問題一覧を取得する。
     * 未指定の検索条件にはデフォルト値を設定する。
     *
     * @param difficulties 難易度の検索条件
     * @param sourceCondition 問題の生成元の検索条件
     * @param structureIds 文法・構造の検索条件
     * @param languageVariants 学習対象言語の検索条件
     * @param japaneseKeyword 日本語の検索キーワード
     * @param chineseKeyword 中国語の検索キーワード
     * @param sortCondition 並び順
     * @param pageable ページング情報
     * @return 条件に一致する管理者用問題一覧
     */
    public Page<AdminQuestionListDto> getFilteredAdminQuestions(
            List<Difficulty> difficulties,
            QuestionSourceCondition sourceCondition,
            List<Long> structureIds,
            List<LanguageVariant> languageVariants,
            String japaneseKeyword,
            String chineseKeyword,
            AdminQuestionSortCondition sortCondition,
            Pageable pageable) {

        // 言語未選択なら全言語
        if (languageVariants == null || languageVariants.isEmpty()) {
            languageVariants = Arrays.asList(LanguageVariant.values());
        }

        // 難易度未選択なら全難易度
        if (difficulties == null || difficulties.isEmpty()) {
            difficulties = Arrays.asList(Difficulty.values());
        }

        // 文法・構造未選択ならすべて選択
        if (structureIds == null || structureIds.isEmpty()) {
            structureIds = structureRepository.findAllStructureIds();
        }

        // キーワード未入力なら空文字
        if (japaneseKeyword == null) {
            japaneseKeyword = "";
        }
        if (chineseKeyword == null) {
            chineseKeyword = "";
        }

        // 問題の生成元が未指定の場合はすべて
        if (sourceCondition == null) {
            sourceCondition = QuestionSourceCondition.ALL;
        }

        // 並び順が未指定の場合は最終更新日時の降順
        if (sortCondition == null) {
            sortCondition = AdminQuestionSortCondition.UPDATED_DESC;
        }

        return questionRepository.findAdminQuestionList(
                searchConditionConverter.convertDifficulty(difficulties),
                sourceCondition.name(),
                structureIds,
                searchConditionConverter.convertLanguageVariant(languageVariants),
                japaneseKeyword,
                chineseKeyword,
                sortCondition.name(),
                pageable);
    }

    /**
     * 指定した問題と関連するお気に入り・学習履歴を削除する。
     *
     * @param questionId 削除する問題ID
     */
    @Transactional
    public void deleteOneQuestion(Long questionId) {

        // 問題に関連する情報を削除
        favoriteRepository.deleteByQuestionQuestionId(questionId);
        studyHistoryRepository.deleteByStudyHistoryKeyQuestionId(questionId);

        // 問題を削除
        questionRepository.deleteById(questionId);

        log.info("問題削除 questionId={}", questionId);
    }

    /**
     * 入力された問題情報をもとに新しい問題を登録する。
     * 中国語の発音表記はAIを使用して生成する。
     *
     * @param form 問題追加画面の入力内容
     */
    @Transactional
    public void addQuestion(QuestionForm form) {
    	
    	// 新しい問題を作成して入力内容と発音表記を設定
        Question question = new Question();
        applyQuestionForm(question, form);

        // 問題を保存
        Question savedQuestion = questionRepository.save(question);

        // 問題登録完了をログに記録
        log.info("問題登録完了 questionId={}", savedQuestion.getQuestionId());
    }

    /**
     * 指定した問題を入力内容で更新する。
     * 中国語の発音表記はAIを使用して再生成する。
     *
     * @param questionId 更新する問題ID
     * @param form 問題編集画面の入力内容
     */
    @Transactional
    public void updateOneQuestion(
            long questionId,
            QuestionForm form) {

        // 更新対象の問題を取得
        Question question =
                questionRepository.findById(questionId)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Question not found."));

        // 入力内容と発音表記を問題に設定
        applyQuestionForm(question, form);

        // 問題を更新
        questionRepository.save(question);

        log.info("問題更新完了 questionId={}", questionId);
    }

    /**
     * 指定した問題の編集前の情報を取得する。
     *
     * @param questionId 問題ID
     * @return 編集前の問題情報
     */
    public OriginalQuestionDTO getOriginalQuestion(long questionId) {

        // 問題を取得
        Question question =
                questionRepository.findById(questionId)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Question not found."));

        // 編集前の問題情報をDTOへ設定
        OriginalQuestionDTO dto = new OriginalQuestionDTO();
        dto.setLanguageVariant(question.getLanguageVariant());
        dto.setJapaneseText(question.getJapaneseText());
        dto.setChineseText(question.getChineseText());
        dto.setAlternativeAnswer(question.getAlternativeAnswer());
        dto.setPinyin(question.getPinyin());
        dto.setZhuyin(question.getZhuyin());
        dto.setAlternativeAnswerPinyin(question.getAlternativeAnswerPinyin());
        dto.setAlternativeAnswerZhuyin(question.getAlternativeAnswerZhuyin());
        dto.setDifficulty(question.getDifficulty());
        dto.setStructureId(question.getStructure().getStructureId());
        dto.setStructureName(question.getStructure().getName());
        dto.setAllowAiVariation(question.getAllowAiVariation());
        dto.setTemplate(question.getTemplate());
        dto.setAiGenerated(question.isAiGenerated());

        // 所有者がいる場合はログインIDをDTOへ設定
        if (question.getOwner() != null) {
            dto.setOwnerLoginId(question.getOwner().getLoginId());
        }

        return dto;
    }

    /**
     * 編集前の問題情報から問題編集画面のフォームを生成する。
     *
     * @param originalQuestion 編集前の問題情報
     * @return 問題編集画面のフォーム
     */
    public QuestionForm createQuestionForm(
            OriginalQuestionDTO originalQuestion) {

        // 編集前の問題情報をフォームへ設定
        QuestionForm form = new QuestionForm();
        form.setLanguageVariant(originalQuestion.getLanguageVariant());
        form.setJapaneseText(originalQuestion.getJapaneseText());
        form.setChineseText(originalQuestion.getChineseText());
        form.setAlternativeAnswer(originalQuestion.getAlternativeAnswer());
        form.setDifficulty(originalQuestion.getDifficulty());
        form.setStructureId(originalQuestion.getStructureId());
        form.setAllowAiVariation(originalQuestion.isAllowAiVariation());
        form.setTemplate(originalQuestion.getTemplate());

        return form;
    }

    /**
     * フォームの入力内容と生成された発音表記を問題に設定する。
     *
     * @param question 設定対象の問題
     * @param form 問題追加・編集画面の入力内容
     * @param pronunciation 生成された発音表記
     */
    private void copyQuestionForm(
            Question question,
            QuestionForm form,
            AiPronunciationResponseDto pronunciation) {

    	// フォームの入力内容と生成した発音表記を問題へ設定
        question.setLanguageVariant(form.getLanguageVariant());
        question.setJapaneseText(form.getJapaneseText());
        question.setChineseText(form.getChineseText());
        question.setAlternativeAnswer(form.getAlternativeAnswer());
        question.setPinyin(pronunciation.getPinyin());
        question.setZhuyin(pronunciation.getZhuyin());
        question.setAlternativeAnswerPinyin(pronunciation.getAlternativeAnswerPinyin());
        question.setAlternativeAnswerZhuyin(pronunciation.getAlternativeAnswerZhuyin());
        question.setDifficulty(form.getDifficulty());
        question.setAllowAiVariation(form.isAllowAiVariation());
        question.setTemplate(form.getTemplate());
    }

    /**
     * フォームの入力内容を問題に反映する。
     * 発音表記をAIで生成し、指定された文法・構造を設定する。
     *
     * @param question 設定対象の問題
     * @param form 問題追加・編集画面の入力内容
     */
    private void applyQuestionForm(
            Question question,
            QuestionForm form) {

        // 発音表記生成用のリクエストを作成
        AiPronunciationRequestDto request =
                new AiPronunciationRequestDto(
                        form.getLanguageVariant(),
                        form.getChineseText(),
                        form.getAlternativeAnswer());

        // 発音表記を生成
        AiPronunciationResponseDto pronunciation =
                aiPronunciationService.generatePronunciation(request);

        // 入力内容と発音表記を問題に設定
        copyQuestionForm(
        		question,
                form,
                pronunciation);

        // 文法・構造を取得して設定
        Structure structure =
                structureRepository
                        .findById(form.getStructureId())
                        .orElseThrow();

        question.setStructure(structure);
    }
}
