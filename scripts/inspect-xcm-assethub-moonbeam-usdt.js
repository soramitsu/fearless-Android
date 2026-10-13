#!/usr/bin/env node

// Read-only source inventory for the frozen Asset Hub -> Moonbeam USDt gap.
// This report never supplies an execution spec or approves a transfer.
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const root = process.env.XCM_DISCOVERY_ROOT || path.resolve(__dirname, '..');
const originId = '68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f';
const destinationId = 'fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d';
const symbol = 'USDT';
const sourcePaths = {
    registry: 'runtime/src/main/assets/local_chains.json',
    gaps: 'scripts/xcm-discovery-only-routes.tsv',
    approved: 'runtime/src/main/assets/approved_xcm_routes.tsv',
    required: 'scripts/xcm-required-routes.tsv',
    productionEvidence: 'scripts/xcm-production-evidence.json'
};

function requireExactlyOne(values, description) {
    if (values.length !== 1) {
        throw new Error(`${description}: expected one, found ${values.length}`);
    }
    return values[0];
}

function normalizedSymbol(value) {
    return typeof value === 'string' ? value.trim().replace(/^xc/i, '').toUpperCase() : '';
}

function tsvRows(source) {
    return source.split(/\r?\n/)
        .map(line => line.split('#', 1)[0])
        .map(line => line.trim())
        .filter(Boolean)
        .map(line => line.split(/\s+/));
}

function sourceAsset(asset) {
    return {
        id: asset.id,
        symbol: asset.symbol,
        precision: asset.precision ?? null,
        currencyId: asset.currencyId ?? null,
        type: asset.type ?? null
    };
}

try {
    if (process.argv.length !== 2 && (process.argv.length !== 4 || process.argv[2] !== '--output')) {
        throw new Error('usage: inspect-xcm-assethub-moonbeam-usdt.js [--output <path>]');
    }

    const sources = {};
    const contents = {};
    for (const [key, relativePath] of Object.entries(sourcePaths)) {
        const bytes = fs.readFileSync(path.join(root, relativePath));
        contents[key] = bytes.toString('utf8');
        sources[key] = {
            path: relativePath,
            sha256: crypto.createHash('sha256').update(bytes).digest('hex'),
            byteLength: bytes.length
        };
    }

    const gap = requireExactlyOne(tsvRows(contents.gaps).filter(row =>
        row[0] === originId && row[1] === destinationId &&
        row[2]?.split(',').map(normalizedSymbol).includes(symbol)
    ), 'discovery-only route');
    if (gap.length !== 5 || gap[2].split(',').length !== 1 ||
        gap[3] !== 'non-native-asset' || gap[4] !== '-') {
        throw new Error('discovery-only route classification changed');
    }

    for (const key of ['approved', 'required']) {
        if (tsvRows(contents[key]).some(row =>
            row[0] === originId && row[1] === destinationId && normalizedSymbol(row[2]) === symbol
        )) {
            throw new Error(`route unexpectedly appears in ${key} execution manifest`);
        }
    }

    const productionEvidence = JSON.parse(contents.productionEvidence);
    if (productionEvidence.status !== 'blocked' || productionEvidence.releaseEnabled !== false) {
        throw new Error('production XCM release gate is no longer blocked');
    }
    if (!Array.isArray(productionEvidence.evidence) || productionEvidence.evidence.some(item =>
        item.originChainId === originId && item.destinationChainId === destinationId &&
        normalizedSymbol(item.assetSymbol) === symbol
    )) {
        throw new Error('route evidence requires separate qualification review');
    }

    const chains = JSON.parse(contents.registry);
    if (!Array.isArray(chains)) throw new Error('bundled chain registry must be an array');
    const origin = requireExactlyOne(chains.filter(chain => chain.chainId === originId), 'origin chain');
    const destination = requireExactlyOne(chains.filter(chain => chain.chainId === destinationId), 'destination chain');
    const advertisedDestination = requireExactlyOne(
        (origin.xcm?.availableDestinations || []).filter(route => route.chainId === destinationId),
        'advertised destination'
    );
    const routeAsset = requireExactlyOne(
        (advertisedDestination.assets || []).filter(asset => normalizedSymbol(asset.symbol) === symbol),
        'advertised route asset'
    );
    const availableAsset = requireExactlyOne(
        (origin.xcm?.availableAssets || []).filter(asset => asset.id === routeAsset.id),
        'origin XCM available asset identity'
    );
    const originAsset = requireExactlyOne(
        (origin.assets || []).filter(asset => normalizedSymbol(asset.symbol) === symbol),
        'origin wallet asset'
    );
    const destinationCandidate = requireExactlyOne(
        (destination.assets || []).filter(asset =>
            typeof asset.symbol === 'string' && asset.symbol.toLowerCase() === 'xcusdt'
        ),
        'possible destination wallet asset'
    );
    if (normalizedSymbol(availableAsset.symbol) !== symbol) {
        throw new Error('origin XCM available asset symbol differs from the route');
    }
    if (routeAsset.execution != null || advertisedDestination.execution != null) {
        throw new Error('route now has execution metadata and needs separate approval review');
    }
    if (advertisedDestination.bridgeParachainId != null) {
        throw new Error('route now advertises a bridge and needs separate review');
    }

    const report = {
        schemaVersion: 1,
        route: { originChainId: originId, destinationChainId: destinationId, assetSymbol: symbol },
        sources,
        discovery: {
            origin: {
                name: origin.name, parentId: origin.parentId ?? null, paraId: origin.paraId ?? null,
                ecosystem: origin.ecosystem, xcmVersion: origin.xcm?.xcmVersion ?? null
            },
            destination: {
                name: destination.name, parentId: destination.parentId ?? null,
                paraId: destination.paraId ?? null, ecosystem: destination.ecosystem
            },
            advertisedRouteAsset: {
                id: routeAsset.id, symbol: routeAsset.symbol, minAmount: routeAsset.minAmount ?? null
            },
            originWalletAsset: sourceAsset(originAsset),
            possibleDestinationWalletAsset: sourceAsset(destinationCandidate)
        },
        qualification: {
            discoveryOnly: true,
            approvedForExecution: false,
            productionEvidenceReleaseEnabled: false,
            remaining: [
                'Verify the Asset Hub USDt identity and Moonbeam xcUSDT relationship against canonical chain state.',
                'Review the exact runtime call, XCM locations, beneficiary, weight, minimum and fee semantics.',
                'Obtain funded origin and destination success evidence with independent on-chain verification.',
                'Approve the exact execution spec and complete the all-routes release gates separately.'
            ]
        }
    };
    const output = `${JSON.stringify(report, null, 2)}\n`;
    if (process.argv[2] === '--output') {
        fs.mkdirSync(path.dirname(process.argv[3]), { recursive: true });
        fs.writeFileSync(process.argv[3], output);
    } else {
        process.stdout.write(output);
    }
} catch (error) {
    console.error(`[xcm-discovery][error] ${error.message}`);
    process.exitCode = 1;
}
