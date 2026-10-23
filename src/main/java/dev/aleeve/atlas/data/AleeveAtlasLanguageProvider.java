package dev.aleeve.atlas.data;

import dev.aleeve.atlas.AleeveAtlas;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/// 生成 `assets/aleeve_atlas/lang/en_us.json`；`zh_cn.json` 为人工维护的源文件，两者键集必须一致（规格 §12.3）。
public class AleeveAtlasLanguageProvider extends LanguageProvider {
    public AleeveAtlasLanguageProvider(PackOutput output) {
        super(output, AleeveAtlas.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // 各功能阶段在此登记 GUI / 键位 / 配置翻译键
    }
}
