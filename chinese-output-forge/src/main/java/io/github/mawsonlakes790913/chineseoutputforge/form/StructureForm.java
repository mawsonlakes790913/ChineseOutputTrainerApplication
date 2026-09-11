package io.github.mawsonlakes790913.chineseoutputforge.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StructureForm {

    @NotBlank(message = "文法・構造名を入力してください。")
    private String name;

    @NotBlank(message = "中国大陸向け説明を入力してください。")
    private String descriptionZhCn;

    @NotBlank(message = "台湾向け説明を入力してください。")
    private String descriptionZhTw;
}
