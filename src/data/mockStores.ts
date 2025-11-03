import { Store } from '../types/store'

// Função auxiliar para gerar logos SVG fictícios
const createLogo = (letter: string, color: string): string => {
  // Usar encodeURIComponent para criar SVG como data URI
  const svgContent = `<svg width="76" height="76" viewBox="0 0 76 76" fill="none" xmlns="http://www.w3.org/2000/svg"><rect width="76" height="76" rx="38" fill="${color}"/><text x="38" y="38" font-family="Arial, sans-serif" font-size="32" font-weight="bold" fill="white" text-anchor="middle" dominant-baseline="central">${letter}</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgContent)}`
}

// Dados mockados de lojas em São Paulo com nomes fictícios e logotipos
export const mockStores: Store[] = [
  {
    id: '1',
    name: 'TABAJARA CENTER',
    address: 'Avenida Paulista, 1000',
    latitude: -23.5614,
    longitude: -46.6564,
    phone: '(11) 3000-0001',
    email: 'center@tabajara.com',
    geocode: '-23.5614, -46.6564',
    logo: createLogo('TC', '#0056B8')
  },
  {
    id: '2',
    name: 'TABAJARA PREMIUM',
    address: 'Rua Augusta, 500',
    latitude: -23.5560,
    longitude: -46.6580,
    phone: '(11) 3000-0002',
    email: 'premium@tabajara.com',
    geocode: '-23.5560, -46.6580',
    logo: createLogo('TP', '#DC143C')
  },
  {
    id: '3',
    name: 'TABAJARA EXPRESS',
    address: 'Avenida Brigadeiro Faria Lima, 2000',
    latitude: -23.5686,
    longitude: -46.6924,
    phone: '(11) 3000-0003',
    email: 'express@tabajara.com',
    geocode: '-23.5686, -46.6924',
    logo: createLogo('TE', '#228B22')
  },
  {
    id: '4',
    name: 'TABAJARA MALL',
    address: 'Rua dos Três Irmãos, 100',
    latitude: -23.5388,
    longitude: -46.6950,
    phone: '(11) 3000-0004',
    email: 'mall@tabajara.com',
    geocode: '-23.5388, -46.6950',
    logo: createLogo('TM', '#FF8C00')
  },
  {
    id: '5',
    name: 'TABAJARA PLUS',
    address: 'Avenida Rebouças, 1500',
    latitude: -23.5515,
    longitude: -46.6780,
    phone: '(11) 3000-0005',
    email: 'plus@tabajara.com',
    geocode: '-23.5515, -46.6780',
    logo: createLogo('T+', '#9370DB')
  },
  {
    id: '6',
    name: 'TABAJARA STORE',
    address: 'Rua Oscar Freire, 800',
    latitude: -23.5610,
    longitude: -46.6700,
    phone: '(11) 3000-0006',
    email: 'store@tabajara.com',
    geocode: '-23.5610, -46.6700',
    logo: createLogo('TS', '#20B2AA')
  },
  {
    id: '7',
    name: 'TABAJARA SHOP',
    address: 'Avenida Consolação, 900',
    latitude: -23.5478,
    longitude: -46.6600,
    phone: '(11) 3000-0007',
    email: 'shop@tabajara.com',
    geocode: '-23.5478, -46.6600',
    logo: createLogo('TS', '#FF1493')
  },
  {
    id: '8',
    name: 'TABAJARA LOJA',
    address: 'Rua Bela Cintra, 600',
    latitude: -23.5530,
    longitude: -46.6650,
    phone: '(11) 3000-0008',
    email: 'loja@tabajara.com',
    geocode: '-23.5530, -46.6650',
    logo: createLogo('TL', '#4169E1')
  },
  {
    id: '9',
    name: 'TABAJARA MEGA',
    address: 'Avenida 9 de Julho, 3000',
    latitude: -23.5650,
    longitude: -46.6800,
    phone: '(11) 3000-0009',
    email: 'mega@tabajara.com',
    geocode: '-23.5650, -46.6800',
    logo: createLogo('TM', '#FF6347')
  },
  {
    id: '10',
    name: 'TABAJARA SUPER',
    address: 'Rua Haddock Lobo, 700',
    latitude: -23.5580,
    longitude: -46.6720,
    phone: '(11) 3000-0010',
    email: 'super@tabajara.com',
    geocode: '-23.5580, -46.6720',
    logo: createLogo('TS', '#32CD32')
  },
  {
    id: '11',
    name: 'TABAJARA SUL',
    address: 'Rua Alcantarilla, 363',
    latitude: -23.6353,
    longitude: -46.7329,
    phone: '(11) 3000-0010',
    email: 'sul@tabajara.com',
    geocode: '-23.6353, -46.7329',
    logo: createLogo('TS', '#32CD32')
  },
  {
    id: '12',
    name: 'TABAJARA RJ',
    address: 'Pr. Saenz Peña, 45 - Tijuca, Rio de Janeiro - RJ',
    latitude: -22.9248,
    longitude: -43.2321,
    phone: '(11) 3000-0010',
    email: 'rj@tabajara.com',
    geocode: '-22.9248, -43.2321',
    logo: createLogo('TS', '#32CD32')
  },
]

