import React from 'react';
import styles from './Citation.module.css';

type CitationProps = React.ComponentPropsWithoutRef<'sup'> & {
  'data-filename'?: string,
  'data-url'?: string,
  'data-pages'?: string
}

export function Citation(props: CitationProps) {
  const filename = props['data-filename'] ?? '';
  const url   = props['data-url'] ?? '';
  const pages = (props['data-pages'] as string)?.split(',').map(s => s.trim()) ?? [];
  return (
    <span className={styles.supWrap}>
      <sup>
        {props.children}
      </sup>

      <span className={styles.tooltip} role="tooltip">
        <a href={url}>{filename}</a>
        { pages.length > 0 && (<div>page{pages.length > 1 && ('s'

        )}: {pages.join(', ')}</div>)}
      </span>
    </span>
  )
}