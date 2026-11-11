# AnimeHub

App Android (Kotlin + Jetpack Compose + Media3 + Room) que reúne sites escolhidos pelo próprio usuário.

## Como rodar
1. Abra a pasta `AnimeHub` no Android Studio (Koala ou mais novo) e sincronize o Gradle.
2. Execute no emulador ou dispositivo (minSdk 24). Testes: `./gradlew test`.

## Como usar
- **Fontes → Adicionar site**: cole só o endereço (ex.: `exemplo.com`). O app abre o site, descobre o nome,
  a URL de busca (por formulário ou endereços comuns) e as obras da página inicial, e já salva.
- **Fontes → ⋮ → Editar**: nome, URL de busca (`{q}` = termo), seletores CSS opcionais de obras e episódios,
  e **Testar configuração**. Também dá para ativar/desativar ou remover cada site.
- **Mesma obra em vários sites**: os resultados são agrupados por título (com selo "N fontes"). Na tela da obra,
  "Assistir em" lista cada fonte com a contagem de episódios, e você escolhe. O app também busca a obra
  nas outras fontes ativas automaticamente (desligável em Ajustes). No player dá para trocar de fonte e de
  qualidade sem perder o ponto do episódio; se um vídeo falha, o app tenta a próxima opção.
- **Baixar obras**: na tela da obra, o botão **Baixar obra** abre uma folha para escolher episódios (todos ou
  alguns), qualidade (melhor/1080p/720p/480p/360p) e idioma das legendas. Os arquivos vão para a pasta
  Downloads/AnimeHub do aparelho. Só links diretos HTTPS (MP4/MKV/WebM) são baixados; HLS/DASH são avisados e ignorados.
  No player também há o download do episódio atual, respeitando a opção de baixar apenas no Wi-Fi definida em Ajustes.
- **Idiomas e qualidade**: o player mostra as faixas de áudio detectadas, as legendas disponíveis, a qualidade
  de cada vídeo e a velocidade de reprodução. As preferências de áudio/legenda e a qualidade dos downloads
  ficam salvas em Ajustes e são aplicadas automaticamente.
- **Ajustes**: tema, cores dinâmicas, busca em outras fontes, HLS preferido, próximo episódio automático,
  idiomas preferidos, qualidade e Wi-Fi dos downloads, bloqueio de capturas de tela.

## Segurança
- Somente HTTPS (TLS 1.2+); `network_security_config` sem tráfego em texto puro e só CAs do sistema.
- URLs digitadas ou extraídas de páginas são validadas: sem IPs privados/localhost/`.local`, sem credenciais
  embutidas; o DNS recusa nomes que resolvem para rede local (anti-SSRF/DNS rebinding).
- Redirecionamento https→http recusado; limite de 5 MB por página; capas e vídeo usam o mesmo cliente seguro.
- Sem backup automático; R8/shrink no release; opção de `FLAG_SECURE`.
- Certificate pinning opcional em `HttpClientFactory.create(pins)`.

## Desempenho
- Cache de páginas em memória (5 min, LRU de 40) com bypass automático nas páginas de episódio e ao puxar para atualizar.
- Cache de capas: memória (20% do heap) e disco (100 MB).
- Busca ao digitar com atraso de 600 ms; fontes consultadas em paralelo com tempo limite e falhas isoladas.
- Listas com `contentType` e chaves estáveis, animação de carregamento (esqueleto) que só redesenha, sem recompor.
- Build: cache e paralelismo do Gradle ativos; R8 + remoção de recursos no release.

## Limites do detector genérico
Funciona com sites HTML comuns. Não executa JavaScript, não contorna Cloudflare/anti-bot nem DRM; nesses casos
o app avisa o motivo. Use "Editar" para ajustar seletores quando a detecção automática errar.

## Estrutura
```
domain/        modelos, TitleMatcher (agrupa a mesma obra), DownloadPlanner, contratos
data/local     Room: sites, favoritos por obra, progresso
data/network   HttpClientFactory, UrlValidator/SafeDns
data/scraper   PageFetcher, ScrapeHeuristics, SiteDetector, GenericWebSource, DemoSource
data/download  DownloadManager (downloads diretos HTTPS)
data/settings  DataStore
presentation/  home, details, player, library, sites, settings
```

## Aviso legal
O app não hospeda conteúdo e não inclui sites prontos (só uma fonte de demonstração com filmes Blender, CC BY).
Use apenas sites e conteúdos que você tem direito de acessar e respeite os termos de cada site.
