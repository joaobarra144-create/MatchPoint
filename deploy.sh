#!/bin/bash
# deploy.sh — Script seguro de deploy para GitHub Pages
# Coloca este ficheiro na RAIZ do projecto Tenis_Web/
# Uso: ./deploy.sh
# NUNCA apaga a tua pasta de trabalho.

set -e  # parar se qualquer comando falhar

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST="$PROJECT_DIR/app/build/dist/wasmJs/productionExecutable"
DEPLOY_DIR="/tmp/matchpoint-ghpages-deploy"
COI_URL="https://cdn.jsdelivr.net/gh/gzuidhof/coi-serviceworker@master/coi-serviceworker.js"

echo "======================================"
echo "  MatchPoint — Deploy para gh-pages"
echo "======================================"
echo ""

# 1. Build
echo "▶ 1/5  Build wasmJs..."
cd "$PROJECT_DIR"
./gradlew :app:wasmJsBrowserDistribution
echo "   ✓ Build concluído"

# 2. Verificar que o dist existe
if [ ! -f "$DIST/index.html" ]; then
    echo "   ✗ ERRO: $DIST/index.html não encontrado. Build falhou?"
    exit 1
fi

# 3. Descarregar coi-serviceworker se não existir já no dist
if [ ! -f "$DIST/coi-serviceworker.js" ]; then
    echo "▶ 2/5  A descarregar coi-serviceworker.js..."
    curl -s -o "$DIST/coi-serviceworker.js" "$COI_URL"
    echo "   ✓ coi-serviceworker.js descarregado"
else
    echo "▶ 2/5  coi-serviceworker.js já existe — a saltar"
fi

# 4. Injectar coi-serviceworker no index.html do dist (se ainda não estiver lá)
if ! grep -q "coi-serviceworker" "$DIST/index.html"; then
    echo "▶ 3/5  A injectar coi-serviceworker no index.html..."
    sed -i 's|</head>|<script src="coi-serviceworker.js"></script></head>|' "$DIST/index.html"
    echo "   ✓ index.html actualizado"
else
    echo "▶ 3/5  coi-serviceworker já está no index.html — a saltar"
fi

# 5. Criar pasta temporária limpa para o deploy (worktree isolado)
echo "▶ 4/5  A preparar worktree gh-pages..."
# Remover worktree anterior se existir
if [ -d "$DEPLOY_DIR" ]; then
    git -C "$PROJECT_DIR" worktree remove "$DEPLOY_DIR" --force 2>/dev/null || rm -rf "$DEPLOY_DIR"
fi

# Criar branch gh-pages remota se não existir
if ! git -C "$PROJECT_DIR" ls-remote --exit-code --heads origin gh-pages > /dev/null 2>&1; then
    echo "   Branch gh-pages não existe — a criar..."
    git -C "$PROJECT_DIR" worktree add --orphan -b gh-pages "$DEPLOY_DIR"
else
    git -C "$PROJECT_DIR" worktree add "$DEPLOY_DIR" gh-pages
    # Limpar conteúdo antigo (só dentro do deploy dir, nunca no projecto!)
    rm -rf "${DEPLOY_DIR:?}"/*
fi
echo "   ✓ Worktree pronto em $DEPLOY_DIR"

# 6. Copiar artefactos para o worktree
echo "▶ 5/5  A copiar artefactos e a fazer push..."
cp -r "$DIST"/. "$DEPLOY_DIR/"

# Adicionar .nojekyll para o GitHub Pages não ignorar ficheiros com underscore
touch "$DEPLOY_DIR/.nojekyll"

cd "$DEPLOY_DIR"
git add -A
git commit -m "deploy: $(date '+%Y-%m-%d %H:%M')"
git push origin gh-pages

# 7. Limpar worktree temporário
git -C "$PROJECT_DIR" worktree remove "$DEPLOY_DIR" --force
cd "$PROJECT_DIR"

echo ""
echo "======================================"
echo "  ✓ Deploy concluído com sucesso!"
echo "  🌐 https://joaobarra144-create.github.io/MatchPoint/"
echo "======================================"
