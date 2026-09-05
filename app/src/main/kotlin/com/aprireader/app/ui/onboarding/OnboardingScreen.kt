package com.aprireader.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aprireader.app.AppContainer
import com.aprireader.app.R
import com.aprireader.app.data.prefs.AppLanguage
import com.aprireader.app.data.prefs.LocaleStore
import com.aprireader.app.ui.common.GlassPanel
import com.aprireader.app.ui.theme.LocalReduceMotion
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquirclePill
import com.aprireader.app.ui.theme.SquircleSm
import kotlinx.coroutines.launch

/**
 * Первый запуск: три лаконичных и приятных шага.
 *
 * 1. Имя пользователя (как обращаться)
 * 2. Выбор языка интерфейса (русский, английский, итальянский, азербайджанский, немецкий)
 * 3. Подключение библиотеки (папка с книгами)
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var language by remember {
        mutableStateOf(
            AppLanguage.fromTag(LocaleStore.read(context)) ?: AppLanguage.suggested(context)
        )
    }
    var folderAdded by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            folderAdded = true
            scope.launch { container.library.addFolder(uri) }
        }
    }

    fun goTo(target: Int) {
        if (step == 0 && name.isNotBlank()) {
            scope.launch { container.settings.update { it.copy(userName = name.trim()) } }
        }
        step = target.coerceIn(0, STEP_COUNT - 1)
    }

    fun applyLanguage(chosen: AppLanguage) {
        language = chosen
        LocaleStore.write(context, chosen.tag)
        scope.launch { container.settings.update { it.copy(languageTag = chosen.tag) } }
    }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            container.settings.update { settings ->
                settings.copy(
                    onboardingCompleted = true,
                    userName = name.trim(),
                    languageTag = language.tag,
                )
            }
            onFinished()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            // Верхняя панель с прогрессом
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepDots(current = step, total = STEP_COUNT)

                if (step < STEP_COUNT - 1) {
                    TextButton(onClick = { finish() }) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            // Основной анимированный контент шагов
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState >= initialState
                    val distance = if (reduceMotion) 0 else 1
                    val enter = slideInHorizontally(tween(360)) { width ->
                        distance * if (forward) width / 3 else -width / 3
                    } + fadeIn(tween(280))
                    val exit = slideOutHorizontally(tween(240)) { width ->
                        distance * if (forward) -width / 4 else width / 4
                    } + fadeOut(tween(180))
                    enter togetherWith exit
                },
                label = "onboarding-steps",
                modifier = Modifier.weight(1f),
            ) { current ->
                when (current) {
                    0 -> NameStep(
                        name = name,
                        onNameChange = { name = it },
                        onDone = { goTo(1) },
                    )
                    1 -> LanguageStep(
                        selected = language,
                        onSelect = { applyLanguage(it) },
                    )
                    else -> LibraryStep(
                        name = name,
                        folderAdded = folderAdded,
                        onPickFolder = { folderLauncher.launch(null) },
                    )
                }
            }

            // Нижняя панель навигации
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(
                        onClick = { goTo(step - 1) },
                        shape = SquirclePill,
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                Button(
                    onClick = { if (step < STEP_COUNT - 1) goTo(step + 1) else finish() },
                    shape = SquirclePill,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (step < STEP_COUNT - 1) R.string.onboarding_next else R.string.onboarding_start
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private const val STEP_COUNT = 3

/** Индикатор шагов: заполненная точка плавно растягивается в пилюлю. */
@Composable
private fun StepDots(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index == current
            val done = index < current
            val width by animateFloatAsState(
                targetValue = if (active) 32f else 10f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dot-$index",
            )
            Box(
                Modifier
                    .width(width.dp)
                    .height(8.dp)
                    .clip(SquirclePill)
                    .background(
                        when {
                            active -> MaterialTheme.colorScheme.primary
                            done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ),
            )
        }
    }
}

@Composable
private fun NameStep(name: String, onNameChange: (String) -> Unit, onDone: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching {
            kotlinx.coroutines.delay(180)
            focusRequester.requestFocus()
        }
    }

    StepScaffold(
        eyebrow = "ApriReader",
        title = stringResource(R.string.onboarding_name_title),
        subtitle = stringResource(R.string.onboarding_name_subtitle),
    ) {
        StaggeredIn(index = 0) {
            GlassPanel(
                shape = SquircleLg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.onboarding_name_label)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        shape = SquircleMd,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onDone() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageStep(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    StepScaffold(
        eyebrow = stringResource(R.string.onboarding_language_title),
        title = stringResource(R.string.onboarding_language_title),
        subtitle = stringResource(R.string.onboarding_language_subtitle),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppLanguage.entries.forEachIndexed { index, lang ->
                val isSelected = selected == lang
                StaggeredIn(index = index) {
                    GlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleLg)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(lang) },
                            ),
                        shape = SquircleLg,
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val flag = when (lang) {
                                AppLanguage.RUSSIAN -> "🇷🇺"
                                AppLanguage.ENGLISH -> "🇬🇧"
                                AppLanguage.ITALIAN -> "🇮🇹"
                                AppLanguage.AZERBAIJANI -> "🇦🇿"
                                AppLanguage.GERMAN -> "🇩🇪"
                            }
                            Text(
                                text = flag,
                                fontSize = 22.sp,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = lang.endonym,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(180)),
                                exit = fadeOut(tween(140)),
                            ) {
                                Surface(
                                    shape = SquirclePill,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryStep(name: String, folderAdded: Boolean, onPickFolder: () -> Unit) {
    val greeting = if (name.isBlank()) {
        stringResource(R.string.onboarding_greeting_unknown)
    } else {
        stringResource(R.string.onboarding_greeting_known, name.trim())
    }

    StepScaffold(
        eyebrow = greeting,
        title = stringResource(R.string.onboarding_library_title),
        subtitle = stringResource(R.string.onboarding_library_subtitle),
    ) {
        StaggeredIn(index = 0) {
            GlassPanel(
                shape = SquircleLg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickFolder),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = SquirclePill,
                        color = if (folderAdded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (folderAdded) Icons.Rounded.Check else Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = if (folderAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(
                            if (folderAdded) R.string.onboarding_folder_connected else R.string.onboarding_pick_folder
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.onboarding_library_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** Общая рамка шага: надзаголовок, заголовок, пояснение и контент. */
@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    eyebrow: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 28.dp),
    ) {
        eyebrow?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(28.dp))
        content()
    }
}

/** Появление с небольшой задержкой для естественного каскада элементов. */
@Composable
private fun StaggeredIn(index: Int, content: @Composable () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    var visible by remember { mutableStateOf(reduceMotion) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            kotlinx.coroutines.delay(50L * index)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + slideInVertically(
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        ) { it / 6 },
    ) {
        content()
    }
}
