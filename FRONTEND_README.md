# Frontend - Localizador Tabajara

Aplicação React TypeScript para visualização de landmarks em um mapa usando MapTiler.

## 🚀 Tecnologias

- React 18
- TypeScript
- Vite
- MapTiler SDK
- MapLibre GL
- Overpass Landmarks API

## 📦 Instalação

1. Instale as dependências:
```bash
npm install
```

2. Configure as variáveis de ambiente:
   - Crie um arquivo `.env` na raiz do projeto
   - Adicione:
     ```env
     # Chave do MapTiler para mapas e geocoding (obrigatória)
     VITE_MAPTILER_API_KEY=sua_chave_maptiler_aqui
     
     # URL da API Overpass Landmarks (URL de produção)
     VITE_LANDMARKS_API_URL=https://overpass-landmarks-production.up.railway.app
     
     # Token de autenticação para o webhook (padrão: supersecret)
     VITE_LANDMARKS_WEBHOOK_TOKEN=supersecret
     ```
   - Obtenha sua chave do MapTiler em: https://www.maptiler.com/

## 🏃 Executar

```bash
npm run dev
```

## 🏗️ Estrutura do Projeto

```
src/
├── components/
│   ├── Map/
│   │   ├── Map.tsx
│   │   └── Map.css
│   └── StoreList/
│       ├── StoreList.tsx
│       └── StoreList.css
├── config/
│   ├── maptiler.ts
│   └── landmarks.ts
├── types/
│   ├── store.ts
│   └── landmark.ts
├── services/
│   └── landmarksService.ts
├── styles/
│   ├── App.css
│   └── index.css
├── App.tsx
└── main.tsx
```

## 🔌 Integração com Overpass Landmarks API

O projeto está preparado para consumir a API Overpass Landmarks.

### Como usar

1. **Busque um endereço** usando a barra de pesquisa
2. **Clique no botão "+"** para adicionar landmarks daquela localização
3. Os landmarks da API Overpass serão adicionados ao mapa

### Endpoints integrados

- **POST /webhook**: Processa coordenadas e busca landmarks (autenticado)
- **GET /landmarks**: Busca landmarks armazenados por coordenadas

### Funcionalidades

- ✅ Busca de landmarks em uma localização
- ✅ Conversão automática de landmarks para o formato Store
- ✅ Combinação de dados mockados com dados da API
- ✅ Prevenção de duplicatas
- ✅ Tratamento de erros

## 📝 Recursos Implementados

- ✅ Visualização em mapa e lista
- ✅ Busca e filtro de lojas
- ✅ Geocoding de endereços (MapTiler)
- ✅ Integração com Overpass Landmarks API
- ✅ Dados mockados + API dinâmica

